package com.intapp.platform.logging;

import com.intapp.platform.logging.logback.CloudWatchLogbackConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers required CloudWatch Configuration bean, if CloudWatch logging is enabled for current application settings
 */
@Slf4j
public class CloudWatchLoggingRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {
    /**
     * Property to enable/disable CloudWatch logging
     */
    public static final String ENABLE_PROPERTY = "logging.cloudwatch.enabled";

    /**
     * Spring environment
     */
    private Environment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableCloudWatchLogging.class.getName(), false)
        );

        if (isEnabled(attributes)) {
            // manually register CloudWatchLoggingConfiguration bean
            GenericBeanDefinition bean = new GenericBeanDefinition();
            bean.setBeanClass(CloudWatchLogbackConfiguration.class);

            registry.registerBeanDefinition("cloudWatchLogbackConfiguration", bean);
        }
    }

    /**
     * Checks if CloudWatch Logging should be enabled in current application run
     * @param attributes {@code @EnableCloudWatchLogging} annotation attributes
     * @return {@code true} if CloudWatch should be enabled; {@code false} otherwise
     */
    private boolean isEnabled(AnnotationAttributes attributes) {
        // check configuration file settings
        if (!environment.getProperty(ENABLE_PROPERTY, Boolean.class, true)) {
            log.debug("CloudWatch logging is disabled via configuration parameter. Skipping...");
            return false;
        }

        // profiles check
        String[] selectedProfiles = attributes.getStringArray("value");
        return environment.acceptsProfiles(selectedProfiles);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
