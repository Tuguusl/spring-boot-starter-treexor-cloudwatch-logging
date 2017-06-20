package com.intapp.platform.logging.logback.appender;

import ch.qos.logback.classic.LoggerContext;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
@Ignore
public class AmazonCloudWatchAppenderTest {
    private static final Logger LOG = LoggerFactory.getLogger(AmazonCloudWatchAppenderTest.class);

    private final static String LOG_GROUP_NAME = "test-group";
    private final static String LOG_STREAM_NAME = "test-stream";
    private final static int TIMEOUT = 5000;

    @Mock
    private AWSLogsClient logsClient;

    @Mock
    private PutLogEventsResult putLogEventsResult;

    @Before
    public void setUp() {
        when(putLogEventsResult.getNextSequenceToken()).thenReturn("");
        when(logsClient.putLogEvents(any())).thenReturn(putLogEventsResult);

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        AmazonCloudWatchAppender appender = new AmazonCloudWatchAppender();
        appender.setAwsLogsClient(logsClient);
        appender.setLogGroup(LOG_GROUP_NAME);
        appender.setLogStream(LOG_STREAM_NAME);
        appender.setContext(loggerContext);
        appender.start();

        loggerContext.getLogger("ROOT").detachAndStopAllAppenders();
        loggerContext.getLogger("ROOT").addAppender(appender);
    }

    @Test
    public void testAppender() {
        //Arrange
        String message = "test";
        ArgumentCaptor<PutLogEventsRequest> args
                = ArgumentCaptor.forClass(PutLogEventsRequest.class);

        //Act
        LOG.info(message);
        verify(logsClient, timeout(TIMEOUT)).putLogEvents(args.capture());

        //Assert
        assertThat(args.getValue().getLogEvents().get(0).getMessage()).contains(message);
        assertThat(args.getValue().getLogGroupName()).isEqualToIgnoringCase(LOG_GROUP_NAME);
        assertThat(args.getValue().getLogStreamName()).isEqualToIgnoringCase(LOG_STREAM_NAME);
    }
}