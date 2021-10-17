package org.folio.service.summary;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.persist.DBClient;

public interface TransactionSummaryService {

  Future<JsonObject> getTransactionSummary(String summaryId, DBClient client);

  Future<Void> setTransactionsSummariesProcessed(JsonObject summary, DBClient client);

  Future<JsonObject> getAndCheckTransactionSummary(String summaryId, DBClient client);

  Integer getNumTransactions(JsonObject summary);

}
