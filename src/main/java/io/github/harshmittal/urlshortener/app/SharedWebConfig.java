package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.shared.id.adapter.out.random.RandomUuidGenerator;
import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.web.ClientIpHasher;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import io.github.harshmittal.urlshortener.shared.web.RequestBodyLimitFilter;
import io.github.harshmittal.urlshortener.shared.web.RequestIdFilter;
import io.github.harshmittal.urlshortener.shared.web.SecurityHeadersFilter;
import io.github.harshmittal.urlshortener.shared.web.SharedExceptionHandler;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Wires the shared kernel's web pieces: request IDs, security headers, the body cap, client IP
 * hashing and the error model.
 */
@Configuration(proxyBeanMethods = false)
class SharedWebConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

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

    /** Second, so even a {@code 413} or {@code 401} carries the headers (R29). */
    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        var registration = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /** Third, still before Spring Security: oversized bodies are refused before authentication (R30, R15). */
    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        var registration = new FilterRegistrationBean<>(new RequestBodyLimitFilter(exceptionResolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Bean
    RequestAuditContexts requestAuditContexts(RequiredEnvironmentCheck.Settings settings) {
        return new RequestAuditContexts(new ClientIpHasher(settings.ipHashSalt()));
    }

    @Bean
    SharedExceptionHandler sharedExceptionHandler() {
        return new SharedExceptionHandler();
    }
}
