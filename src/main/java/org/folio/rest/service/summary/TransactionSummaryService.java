package org.folio.rest.service.summary;

import io.vertx.core.Context;
import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

import java.util.Map;

public interface TransactionSummaryService<T extends Entity> {

  Future<T> getAndCheckTransactionSummary(Transaction transaction, Context context, Map<String, String> headers);
  Future<Void> setTransactionsSummariesProcessed(T summary, DBClient client);
  Integer getNumTransactions(T summary);
}
