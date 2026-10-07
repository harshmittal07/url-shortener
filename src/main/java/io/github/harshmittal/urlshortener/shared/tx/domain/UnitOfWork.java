package io.github.harshmittal.urlshortener.shared.tx.domain;

import java.util.function.Supplier;

/** Transaction boundary, so domain services stay free of Spring (plan §1). */
public interface UnitOfWork {

    /** Runs the work in a transaction, joining one that is already active. Rolls back if it throws. */
    <T> T inTransaction(Supplier<T> work);

    /** Runs the work in a new transaction that commits even if an enclosing transaction rolls back. */
    void requiresNew(Runnable work);
}
