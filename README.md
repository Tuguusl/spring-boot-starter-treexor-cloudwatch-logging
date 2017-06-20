## Amazon CloudWatch Logging
Amazon CloudWatch Logging dependency adds ability to automatically sent your log messages to AWS CloudWatch storage.
_Currently, only support for Logback is present_

## Workflow

### Dependency

Amazon CloudWatch Logging dependency resides at Intapp Artifactory repository, so to pull it your should have
valid credentials (_please, consult your system administrator if you don't have one yet_).

#### Gradle example
To include this dependency into your Gradle project, please, setup artifactory plugin and specify dependency as follows:

```groovy
// your configuration

repositories {
    mavenCentral()
    maven {
        url "${artifactory_contextUrl}/${artifactory_repo}"
        credentials {
            username "${artifactory_user}"
            password "${artifactory_password}"
        }
    }
}

dependencies {
    // other dependencies

    // this dependency
    compile group: 'com.intapp.platform', name: 'cloudwatch-logging-spring-boot-starter', version: "$latestAvailableVersion"
}
```

This artifact is build with and depends on next libraries:
* _spring-boot-starter_, version: _1.5.3.RELEASE_
* _aws-java-sdk-logs_ and _aws-java-sdk-cloudwatch_, version: _1.11.18_
* _guava_, version: _21.0_

If you already have some (or all) of this dependencies in your classpath, you can force _Gradle_ to *exclude*
some (or all) dependencies as follows:
```groovy
dependencies {
    // other dependencies

    // this exclude AWS dependencies
    compile("com.intapp.platform:cloudwatch-logging-spring-boot-starter:$latestAvailableVersion") {
        exclude group: 'com.amazonaws'
    }
    
    // exclude all transitive dependencies
    compile group: 'com.intapp.platform', name: 'cloudwatch-logging-spring-boot-starter', version: "$latestAvailableVersion", transitive: false
}
```

### Maven example
TBD

## Usage

### Logback XML configuration
You can specify, that CloudWatch appender should be used for logging in Logback XML configuration file 
(_logback.xml_ or _logback-spring.xml_). See example for Spring-based application with profile support:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true">

    <!-- resolve CloudWatch settings from configuration -->
    <springProperty name="logGroup" source="logging.cloudwatch.log-group"/>
    <springProperty name="logStream" source="logging.cloudwatch.log-stream"/>
    <springProperty name="awsRegion" source="logging.cloudwatch.region"/>
    <springProperty name="logPattern" source="logging.cloudwatch.pattern"/>

    <!-- for dev profile print logs in console -->
    <springProfile name="dev">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d %-5level --- [%15.15thread] %-40.40logger{40} : %msg%n</pattern>
            </encoder>
        </appender>

        <root>
            <appender-ref ref="STDOUT"/>
        </root>
    </springProfile>


    <!-- for cloud profile save logs in CloudWatch -->
    <springProfile name="cloud">
        <!-- appender from this dependency goes here -->
        <appender name="CLOUDWATCH" class="com.intapp.platform.logging.logback.appender.AmazonCloudWatchAppender">
            <region>${awsRegion}</region>
            <logGroup>${logGroup:- }</logGroup>
            <logStream>${logStream:- }</logStream>
            <layout class="ch.qos.logback.classic.PatternLayout">
                <pattern>${logPattern:-%msg%n}</pattern>
            </layout>
        </appender>

        <root>
            <appender-ref ref="CLOUDWATCH"/>
        </root>
    </springProfile>
</configuration>

```
Main benefit of such approach:
* starts earlier, than two next approaches (as soon as Spring configuration is resolved), 
  so you will be able to see more logs related to application bootstrap
* allows to use full power of Logback configuration

### @EnableCloudWatchLogging annotation
Placing _@EnableCloudWatchLogging_ on main class of your application will enable CloudWatch logging for your service
in specified profile(s).
```java
@SpringBootApplication
@EnableCloudWatchLogging("production")                  // one profile
@EnableCloudWatchLogging({"cloud", "prod"})             // any of profiles
public class MainApplication {
}
```
CloudWatch logging can be enabled/disabled via configuration parameter:
```yaml
logging:
  cloudwatch:
    enabled: false # default value - unspecified (considered as false)
```

Main benefit of such approach:
* easy to use and enable/disable


### Manual bean registration
You can manually create _CloudWatchLogbackConfigurationAdapter_ (or override it) to have more control of logging behavior:
```java
@Autowired  // configure as you wish
private CloudWatchProperties properties;

@Bean
public CloudWatchLogbackConfiguration cloudWatchLogbackConfiguration() {
    return new CloudWatchLogbackConfiguration(properties);
}
```

Main benefit of such approach:
* control about CloudWatch settings (_log-group_, _log-stream_, _region_, _pattern_)