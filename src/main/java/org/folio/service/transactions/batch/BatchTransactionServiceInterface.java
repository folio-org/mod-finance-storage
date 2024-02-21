package org.folio.service.transactions.batch;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;

import java.util.List;

public interface BatchTransactionServiceInterface {

  void prepareCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder);

  void prepareUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder);

  TransactionType getTransactionType();

}
