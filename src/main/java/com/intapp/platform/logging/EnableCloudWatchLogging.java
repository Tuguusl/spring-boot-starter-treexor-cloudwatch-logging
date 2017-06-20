package com.intapp.platform.logging;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables CloudWatch logging for project.
 * <br/>
 * You must specify profiles, for which logging should be enabled as annotation attribute(s).
 * CloudWatch logging can be explicitly disabled setting property {@code logging.cloudwatch.enabled} to {@code false}
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(CloudWatchLoggingRegistrar.class)
public @interface EnableCloudWatchLogging {
    /**
     * Profiles, for which CloudWatch logging should be enabled
     */
    String[] value();
}
