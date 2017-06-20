package com.intapp.platform.logging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Properties for CloudWatch
 */
@Data
@Component
@ConfigurationProperties(prefix = "logging.cloudwatch")
public class CloudWatchProperties {
    private String logGroup;

    private String logStream;

    private String region;

    private String pattern;
}
