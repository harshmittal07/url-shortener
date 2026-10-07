package io.github.harshmittal.urlshortener.app;

import io.github.harshmittal.urlshortener.shared.audit.adapter.out.persistence.JdbcAuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditSink;
import io.github.harshmittal.urlshortener.shared.audit.domain.AuditTrail;
import io.github.harshmittal.urlshortener.shared.id.domain.IdGenerator;
import io.github.harshmittal.urlshortener.shared.tx.adapter.out.spring.TransactionTemplateUnitOfWork;
import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/** Wires transactions and the audit trail (shared kernel). */
@Configuration(proxyBeanMethods = false)
class AuditConfig {

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new TransactionTemplateUnitOfWork(transactionManager);
    }

    @Bean
    AuditSink auditSink(JdbcClient jdbc) {
        return new JdbcAuditSink(jdbc);
    }

    @Bean
    AuditTrail auditTrail(AuditSink sink, UnitOfWork unitOfWork, Clock clock, IdGenerator ids) {
        return new AuditTrail(sink, unitOfWork, clock, ids);
    }
}
