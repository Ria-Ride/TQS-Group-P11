package pt.ua.deti.tqs.p11.application;

import java.util.function.Supplier;

/**
 * Port: runs work atomically, all or nothing. The use cases say what must be atomic; an adapter
 * (Spring's TransactionTemplate) does it, so the application layer stays free of Spring (K5).
 */
public interface TransactionRunner {

  <T> T inTransaction(Supplier<T> work);
}
