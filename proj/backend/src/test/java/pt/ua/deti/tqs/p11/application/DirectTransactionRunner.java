package pt.ua.deti.tqs.p11.application;

import java.util.function.Supplier;

/**
 * Runs the work straight away. Unit tests have no database, so there is nothing to make atomic;
 * atomicity and locking are tested against PostgreSQL in the integration tests.
 */
public final class DirectTransactionRunner implements TransactionRunner {

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return work.get();
  }
}
