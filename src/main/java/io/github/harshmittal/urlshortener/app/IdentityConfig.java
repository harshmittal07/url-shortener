package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.identity.adapter.in.web.KeyController;
import io.github.harshmittal.urlshortener.shared.identity.adapter.out.persistence.JdbcApiKeyRepository;
import io.github.harshmittal.urlshortener.shared.identity.adapter.out.random.SecureRandomKeyMaterialGenerator;
import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKeyAuthenticator;
import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKeyIssuer;
import io.github.harshmittal.urlshortener.shared.identity.domain.ApiKeyRepository;
import io.github.harshmittal.urlshortener.shared.identity.domain.Authenticator;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import io.github.harshmittal.urlshortener.shared.web.RequestAuditContexts;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires API keys: storage, issuing and authentication (shared kernel, schema identity). */
@Configuration(proxyBeanMethods = false)
class IdentityConfig {

    @Bean
    ApiKeyRepository apiKeyRepository(JdbcClient jdbc) {
        return new JdbcApiKeyRepository(jdbc);
    }

    @Bean
    Authenticator authenticator(ApiKeyRepository keys, RequiredEnvironmentCheck.Settings settings) {
        return new ApiKeyAuthenticator(keys, settings.bootstrapAdminKeyHash());
    }

    @Bean
    ApiKeyIssuer apiKeyIssuer(
            ApiKeyRepository keys, AuditTrail audit, UnitOfWork unitOfWork, Clock clock, IdGenerator ids) {
        return new ApiKeyIssuer(keys, new SecureRandomKeyMaterialGenerator(), audit, unitOfWork, clock, ids);
    }

    @Bean
    KeyController keyController(ApiKeyIssuer issuer, RequestAuditContexts auditContexts) {
        return new KeyController(issuer, auditContexts);
    }
}
