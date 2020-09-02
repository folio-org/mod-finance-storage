package org.folio.dao.summary;

import io.vertx.core.json.JsonObject;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface TransactionSummaryDao {

  Future<JsonObject> getSummaryById(String summaryId, DBClient client);

  Future<Void> updateSummaryInTransaction(JsonObject summary, DBClient client);
}
