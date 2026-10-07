package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.link.adapter.in.web.CreationRateLimitInterceptor;
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
import io.github.harshmittal.urlshortener.shared.ratelimit.adapter.out.bucket4j.Bucket4jRateLimiter;
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
    UrlPolicy urlPolicy(RequiredEnvironmentCheck.Settings settings) {
        return new StandardUrlPolicy(settings.publicBaseUrl());
    }

    /** Daily link quota per key (spec 02); {@code LINK_DAILY_QUOTA}, default 500 (D16). */
    @Bean
    LinkService linkService(
            LinkRepository links,
            ShortCodeGenerator codes,
            UrlPolicy urlPolicy,
            AuditTrail audit,
            UnitOfWork unitOfWork,
            Clock clock,
            IdGenerator ids,
            @Value("${url-shortener.link-daily-quota}") String dailyQuota) {
        return new LinkService(
                links, codes, urlPolicy, audit, unitOfWork, clock, ids, DailyQuotaSetting.parse(dailyQuota));
    }

    @Bean
    LinkLookup linkLookup(LinkRepository links) {
        return new LinkLookupService(links);
    }

    @Bean
    LinkController linkController(
            LinkService links, RequestAuditContexts auditContexts, RequiredEnvironmentCheck.Settings settings) {
        return new LinkController(links, auditContexts, settings.publicBaseUrl());
    }

    /** Per-key creation limit (R15); {@code LINK_CREATE_LIMIT_PER_MINUTE}, default 30 (D16). */
    @Bean
    CreationRateLimitInterceptor creationRateLimitInterceptor(
            AuditTrail audit,
            RequestAuditContexts auditContexts,
            Clock clock,
            @Value("${url-shortener.link-create-limit-per-minute}") int limitPerMinute) {
        return new CreationRateLimitInterceptor(new Bucket4jRateLimiter(limitPerMinute, clock), audit, auditContexts);
    }

    @Bean
    LinkExceptionHandler linkExceptionHandler() {
        return new LinkExceptionHandler();
    }
}
