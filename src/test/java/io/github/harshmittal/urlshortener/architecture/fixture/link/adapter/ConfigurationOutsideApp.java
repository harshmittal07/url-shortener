package io.github.harshmittal.urlshortener.architecture.fixture.link.adapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfigurationOutsideApp {
    @Bean
    Object bean() {
        return new Object();
    }
}
