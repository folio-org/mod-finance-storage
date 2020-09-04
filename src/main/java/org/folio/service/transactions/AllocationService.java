package org.folio.service.transactions;

import org.folio.rest.jaxrs.model.Transaction;

public class AllocationService extends DefaultTransactionService implements TransactionManagingStrategy {
  @Override
  public Transaction.TransactionType getStrategyName() {
    return Transaction.TransactionType.ALLOCATION;
  }

}
