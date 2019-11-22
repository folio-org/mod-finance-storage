package org.folio.rest.transaction;

import org.folio.rest.jaxrs.model.Transaction;

public interface TransactionHandler {

  void createTransaction(Transaction transaction);

  void updateTransaction(Transaction transaction);
}
