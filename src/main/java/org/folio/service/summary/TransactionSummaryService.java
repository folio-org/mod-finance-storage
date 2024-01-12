package org.folio.service.summary;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;

public interface TransactionSummaryService {

  Future<JsonObject> getTransactionSummary(String summaryId, DBConn conn);

  Future<JsonObject> getTransactionSummaryWithLocking(String summaryId, DBConn conn);

  Future<Void> setTransactionsSummariesProcessed(JsonObject summary, DBConn conn);

  Future<JsonObject> getAndCheckTransactionSummary(Transaction transaction, DBConn conn);

  Integer getNumTransactions(JsonObject summary);

  String getSummaryId(Transaction transaction);

}
