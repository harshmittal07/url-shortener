package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.link.adapter.in.web.LinkController;
import io.github.harshmittal.urlshortener.link.adapter.in.web.LinkExceptionHandler;
import io.github.harshmittal.urlshortener.link.adapter.out.persistence.JdbcLinkRepository;
import io.github.harshmittal.urlshortener.link.adapter.out.random.SecureRandomShortCodeGenerator;
import io.github.harshmittal.urlshortener.link.api.LinkLookup;
import io.github.harshmittal.urlshortener.link.domain.LinkLookupService;
import io.github.harshmittal.urlshortener.link.domain.LinkRepository;
import io.github.harshmittal.urlshortener.link.domain.LinkService;
import io.github.harshmittal.urlshortener.link.domain.ShortCodeGenerator;
import io.github.harshmittal.urlshortener.link.domain.StandardUrlPolicy;
import io.github.harshmittal.urlshortener.link.domain.UrlPolicy;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the link module (schema link). */
@Configuration(proxyBeanMethods = false)
class LinkConfig {

    @Bean
    LinkRepository linkRepository(JdbcClient jdbc) {
        return new JdbcLinkRepository(jdbc);
    }

    @Bean
    ShortCodeGenerator shortCodeGenerator() {
        return new SecureRandomShortCodeGenerator();
    }

    @Bean
    UrlPolicy urlPolicy(@Value("${url-shortener.public-base-url}") String publicBaseUrl) {
        return new StandardUrlPolicy(publicBaseUrl);
    }

    @Bean
    LinkService linkService(
            LinkRepository links,
            ShortCodeGenerator codes,
            UrlPolicy urlPolicy,
            AuditTrail audit,
            UnitOfWork unitOfWork,
            Clock clock,
            IdGenerator ids) {
        return new LinkService(links, codes, urlPolicy, audit, unitOfWork, clock, ids);
    }

    @Bean
    LinkLookup linkLookup(LinkRepository links) {
        return new LinkLookupService(links);
    }

    @Bean
    LinkController linkController(
            LinkService links,
            RequestAuditContexts auditContexts,
            @Value("${url-shortener.public-base-url}") String publicBaseUrl) {
        return new LinkController(links, auditContexts, publicBaseUrl);
    }

    @Bean
    LinkExceptionHandler linkExceptionHandler() {
        return new LinkExceptionHandler();
    }
}
