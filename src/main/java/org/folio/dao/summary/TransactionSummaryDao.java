package org.folio.dao.summary;

import io.vertx.core.json.JsonObject;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public interface TransactionSummaryDao {

  Future<JsonObject> getSummaryById(String summaryId, DBConn conn);

  Future<JsonObject> getSummaryByIdWithLocking(String summaryId, DBConn conn);

  Future<Void> createSummary(JsonObject summary, DBConn conn);

  Future<Void> updateSummary(JsonObject summary, DBConn conn);
}
