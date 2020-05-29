package org.folio.dao.summary;

import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface TransactionSummaryDao<T>{

  Future<T> getSummaryById(String summaryId, DBClient client);

  Future<Void> updateSummaryInTransaction(T summary, DBClient client);
}
