package com.intapp.platform.logging;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@code ComponentScan} for this dependency packages.
 *
 * <p>This configuration is enabled from <i>spring.factories</i> resource on dependency include
 */
@Configuration
@ComponentScan
public class CloudWatchLoggingAutoConfiguration {
}
