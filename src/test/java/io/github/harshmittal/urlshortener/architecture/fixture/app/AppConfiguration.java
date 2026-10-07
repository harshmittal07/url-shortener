package io.github.harshmittal.urlshortener.architecture.fixture.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AppConfiguration {
    @Bean
    Object bean() {
        return new Object();
    }
}
