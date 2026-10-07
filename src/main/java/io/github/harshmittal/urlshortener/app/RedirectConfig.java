package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import io.github.harshmittal.urlshortener.redirect.adapter.in.web.RedirectController;
import io.github.harshmittal.urlshortener.redirect.domain.RedirectService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the redirect module. It sees the link module only as {@link LinkLookup}. */
@Configuration(proxyBeanMethods = false)
class RedirectConfig {

    @Bean
    RedirectService redirectService(LinkLookup links) {
        return new RedirectService(links);
    }

    @Bean
    RedirectController redirectController(RedirectService redirects) {
        return new RedirectController(redirects);
    }
}
