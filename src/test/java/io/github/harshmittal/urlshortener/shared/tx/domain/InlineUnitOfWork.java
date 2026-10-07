package io.github.harshmittal.urlshortener.shared.tx.domain;

import java.util.function.Supplier;

/** Runs work inline with no transaction. For unit tests of services; rollback is not simulated. */
public final class InlineUnitOfWork implements UnitOfWork {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    public void requiresNew(Runnable work) {
        work.run();
    }
}
