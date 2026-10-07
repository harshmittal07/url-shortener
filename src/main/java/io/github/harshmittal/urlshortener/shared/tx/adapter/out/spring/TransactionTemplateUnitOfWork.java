package io.github.harshmittal.urlshortener.shared.tx.adapter.out.spring;

import io.github.harshmittal.urlshortener.shared.tx.domain.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionTemplateUnitOfWork implements UnitOfWork {

    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;

    public TransactionTemplateUnitOfWork(PlatformTransactionManager transactionManager) {
        this.required = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return required.execute(status -> work.get());
    }

    @Override
    public void requiresNew(Runnable work) {
        requiresNew.executeWithoutResult(status -> work.run());
    }
}
