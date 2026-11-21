package pt.ua.deti.tqs.p11.adapter.out.persistence;

import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;
import pt.ua.deti.tqs.p11.application.TransactionRunner;

/**
 * TransactionRunner with Spring's TransactionTemplate: commits when the work returns, rolls back
 * when it throws (the exception is passed on). Row locks taken inside last until then.
 */
public class SpringTransactionRunner implements TransactionRunner {

  private final TransactionTemplate template;

  public SpringTransactionRunner(TransactionTemplate template) {
    this.template = template;
  }

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    return template.execute(status -> work.get());
  }
}
