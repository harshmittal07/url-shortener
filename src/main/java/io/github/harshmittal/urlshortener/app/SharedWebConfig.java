package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.shared.id.adapter.out.random.RandomUuidGenerator;
import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.web.RequestIdFilter;
import io.github.harshmittal.urlshortener.shared.web.SharedExceptionHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Wires the shared kernel's web pieces: request IDs and the error model. */
@Configuration(proxyBeanMethods = false)
class SharedWebConfig {

    @Bean
    IdGenerator idGenerator() {
        return new RandomUuidGenerator();
    }

    /** Runs first, before Spring Security, so rejected requests carry an ID too. */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter(IdGenerator idGenerator) {
        var registration = new FilterRegistrationBean<>(new RequestIdFilter(idGenerator));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
        return registration;
    }

    @Bean
    SharedExceptionHandler sharedExceptionHandler() {
        return new SharedExceptionHandler();
    }
}
