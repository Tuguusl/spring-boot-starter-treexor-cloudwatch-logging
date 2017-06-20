package com.intapp.platform.logging.logback.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.layout.EchoLayout;
import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;
import com.amazonaws.util.StringUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Queues;
import lombok.NonNull;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

@SuppressWarnings({
        "checkstyle:com.puppycrawl.tools.checkstyle.checks.metrics.ClassDataAbstractionCouplingCheck",
        "pmd:BeanMembersShouldSerialize"
})
public class AmazonCloudWatchAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final int DEFAULT_MAX_BATCH_SIZE = 512;
    private static final int DEFAULT_MAX_BATCH_TIME = 1000;
    private static final int DEFAULT_INTERNAL_QUEUE_SIZE = 8192;
    private static final int DEFAULT_MAX_FLUSH_TIME = 3000;

    private static final String DEFAULT_NAME = "CLOUDWATCH";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String SHUTDOWN_HOOK_THREAD_NAME = "cloudwatch-appender-shutdown-hook";

    /**
     * Name of the CloudWatch log group.
     */
    @NonNull
    @Setter
    private String logGroup;

    /**
     * Name of the CloudWatch log stream.
     */
    @NonNull
    @Setter
    private String logStream;

    /**
     * AWS region to which logs will be published.
     */
    @Setter
    private String region;

    /**
     * AWS API client which will be used for log publishing.
     * Default one will be created if another isn't provided.
     */
    @Setter
    private AWSLogsClient awsLogsClient;

    /**
     * Logback layout for log lines formatting.
     */
    @Setter
    private Layout<ILoggingEvent> layout;

    private LinkedBlockingQueue<InputLogEvent> logEventsQueue;
    private Worker worker;
    private Thread shutdownHook;

    public AmazonCloudWatchAppender() {
        this(DEFAULT_NAME);
    }

    public AmazonCloudWatchAppender(String name) {
        this.name = name;
    }

    @Override
    public void start() {
        if (StringUtils.isNullOrEmpty(logGroup)) {
            String message = format("CloudWatch log group name is not set for appender %s", getName());

            addWarn(message);
            System.err.println(message);    // duplicating error message in console as there can be no more appenders
            return;
        }

        if (StringUtils.isNullOrEmpty(logStream)) {
            String message = format("CloudWatch log stream name is not set for appender %s", getName());

            addWarn(message);
            System.err.println(message);    // duplicating error message in console as there can be no more appenders
            return;
        }
        if (RegionUtils.getRegion(region) == null) {
            region = DEFAULT_REGION;

            String message = format("AWS region is not set for appender, falling back to %s", region);

            addWarn(message);
            System.err.println(message);    // duplicating error message in console as there can be no more appenders
        }

        if (layout == null) {
            layout = new EchoLayout<>();

            String message = format("No layout set for CloudWatch appender, falling back to %s", layout);

            addWarn(message);
            System.err.println(message);    // duplicating error message in console as there can be no more appenders
        }

        if (awsLogsClient == null) {
            awsLogsClient = new AWSLogsClient();
        }

        doStart();
        super.start();
    }

    private void doStart() {
        try {
            awsLogsClient.setRegion(RegionUtils.getRegion(region));
            try {
                awsLogsClient.createLogGroup(new CreateLogGroupRequest().withLogGroupName(logGroup));
            } catch (ResourceAlreadyExistsException ex) {
                addInfo(ex.getMessage(), ex);
            }
            try {
                awsLogsClient.createLogStream(new CreateLogStreamRequest().withLogGroupName(logGroup).withLogStreamName(logStream));
            } catch (ResourceAlreadyExistsException ex) {
                addInfo(ex.getMessage(), ex);
            }

        } catch (AmazonClientException ex) {
            this.awsLogsClient = null;
            addError(ex.getMessage(), ex);
            return;
        }

        logEventsQueue = new LinkedBlockingQueue<>(DEFAULT_INTERNAL_QUEUE_SIZE);

        shutdownHook = new Thread(this::stop);
        shutdownHook.setName(SHUTDOWN_HOOK_THREAD_NAME);
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        worker = new Worker(this);
        worker.setName(format("%s-worker", getName()));
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        if (isStarted()) {
            if (worker != null) {
                worker.stopGracefully();
                try {
                    worker.join(DEFAULT_MAX_FLUSH_TIME);
                    if (worker.isAlive()) {
                        addWarn(format(
                                "Max queue flush timeout (%d ms) exceeded, approximately %d queued events were possibly "
                                        + "discarded",
                                DEFAULT_MAX_FLUSH_TIME, logEventsQueue.size()));
                    }
                } catch (InterruptedException ex) {
                    addError(format("Stopping was interrupted, approximately %d queued events may be discarded",
                            logEventsQueue.size()), ex);
                }
                worker = null;
            }

            if (shutdownHook != null && !SHUTDOWN_HOOK_THREAD_NAME.equals(Thread.currentThread().getName())) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }

            if (logEventsQueue != null) {
                logEventsQueue.clear();
                logEventsQueue = null;
            }

            if (awsLogsClient != null) {
                awsLogsClient.shutdown();
                awsLogsClient = null;
            }

            super.stop();
            layout.stop();
        }
    }

    private void handle(final ILoggingEvent event) throws Exception {
        InputLogEvent logEvent = new InputLogEvent().withTimestamp(event.getTimeStamp()).withMessage(layout.doLayout(event));
        if (!logEventsQueue.offer(logEvent, DEFAULT_MAX_FLUSH_TIME, TimeUnit.MILLISECONDS)) {
            addWarn(format("No space available in internal queue after %d ms waiting, logging event was discarded",
                    DEFAULT_MAX_FLUSH_TIME));
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            handle(event);
        } catch (Exception ex) {
            this.started = false;
            addError(format("Failed to handle logging event for '%s'", getName()), ex);
        }
    }


    private static class Worker extends Thread {

        private static final Ordering<InputLogEvent> ORDERING = new Ordering<InputLogEvent>() {
            @Override
            public int compare(InputLogEvent left, InputLogEvent right) {
                return left.getTimestamp().compareTo(right.getTimestamp());
            }
        };

        private final AmazonCloudWatchAppender parent;

        private String token = null;
        private volatile boolean started = false;

        Worker(AmazonCloudWatchAppender parent) {
            this.parent = parent;
        }

        @Override
        public void run() {
            started = true;

            while (started) {
                List<InputLogEvent> events = new LinkedList<>();
                try {
                    Queues.drain(parent.logEventsQueue, events, DEFAULT_MAX_BATCH_SIZE, DEFAULT_MAX_BATCH_TIME, TimeUnit.MILLISECONDS);
                    handle(events);
                } catch (InterruptedException ex) {
                    handle(events);
                }
            }

            List<InputLogEvent> remaining = new LinkedList<>();
            parent.logEventsQueue.drainTo(remaining);
            Lists.partition(remaining, DEFAULT_MAX_BATCH_SIZE).forEach(this::handle);
        }

        void stopGracefully() {
            started = false;
        }

        private void handle(List<InputLogEvent> events) {
            if (!events.isEmpty()) {
                List<InputLogEvent> sorted = ORDERING.immutableSortedCopy(events);
                PutLogEventsRequest request = new PutLogEventsRequest(parent.logGroup, parent.logStream, sorted);
                try {
                    try {
                        putEvents(request);
                    } catch (DataAlreadyAcceptedException | InvalidSequenceTokenException ex) {
                        putEvents(request);
                    }
                } catch (Exception ex) {
                    parent.addError(format("Failed to handle %d events", events.size()), ex);
                }
            }
        }

        private void putEvents(PutLogEventsRequest request) {
            try {
                PutLogEventsResult result = parent.awsLogsClient.putLogEvents(request.withSequenceToken(token));
                token = result.getNextSequenceToken();
            } catch (DataAlreadyAcceptedException ex) {
                token = ex.getExpectedSequenceToken();
                throw ex;
            } catch (InvalidSequenceTokenException ex) {
                token = ex.getExpectedSequenceToken();
                throw ex;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AmazonCloudWatchAppender) {
            AmazonCloudWatchAppender otherAppender = (AmazonCloudWatchAppender) obj;

            return Objects.equals(this.name, otherAppender.name);
        }

        return super.equals(obj);
    }
}