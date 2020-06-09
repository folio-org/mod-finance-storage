package org.folio.service.summary;

import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface TransactionSummaryService<T extends Entity> {

  Future<T> getTransactionSummary(Transaction transaction, DBClient client);

  Future<Void> setTransactionsSummariesProcessed(T summary, DBClient client);

  Integer getNumTransactions(T summary);

  Future<T> getAndCheckTransactionSummary(Transaction transaction, DBClient client);
}
