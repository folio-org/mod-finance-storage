package org.folio.service.summary;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;

public interface TransactionSummaryService {

  Future<JsonObject> getTransactionSummary(String summaryId, DBClient client);

  Future<JsonObject> getTransactionSummaryWithLocking(String summaryId, Conn conn);

  Future<Void> setTransactionsSummariesProcessed(JsonObject summary, DBClient client);

  Future<JsonObject> getAndCheckTransactionSummary(Transaction transaction, DBClient client);

  Integer getNumTransactions(JsonObject summary);

  String getSummaryId(Transaction transaction);

}
