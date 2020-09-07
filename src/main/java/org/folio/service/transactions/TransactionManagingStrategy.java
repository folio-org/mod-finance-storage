package org.folio.service.transactions;

import org.folio.rest.jaxrs.model.Transaction;

public interface TransactionManagingStrategy extends TransactionService {
  Transaction.TransactionType getStrategyName();
}
