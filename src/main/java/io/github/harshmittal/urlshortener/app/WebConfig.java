package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.link.adapter.in.web.CreationRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC interceptors. Interceptors run after the security filters and before body binding. */
@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    private final CreationRateLimitInterceptor creationRateLimit;

    WebConfig(CreationRateLimitInterceptor creationRateLimit) {
        this.creationRateLimit = creationRateLimit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // The interceptor itself counts only POST.
        registry.addInterceptor(creationRateLimit).addPathPatterns("/api/links");
    }
}
