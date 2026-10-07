package io.github.harshmittal.urlshortener.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Verifies the required environment variables (R22). Beans that need them take {@link
 * RequiredEnvironmentCheck.Settings}, so nothing reads a variable before the check has run.
 */
@Configuration(proxyBeanMethods = false)
class EnvironmentConfig {

    @Bean
    RequiredEnvironmentCheck.Settings requiredSettings(Environment environment) {
        return RequiredEnvironmentCheck.verify(environment::getProperty);
    }
}
