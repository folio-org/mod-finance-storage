package org.folio.dao.transactions;

import io.vertx.core.json.JsonObject;

import java.util.List;

public class DefaultTransactionDAO extends BaseTransactionDAO {
  @Override
  protected String createPermanentTransactionsQuery(String tenantId) {
    throw new RuntimeException("createPermanentTransactionsQuery: not implemented in DefaultTransactionDAO");
  }

  @Override
  protected String buildUpdatePermanentTransactionQuery(List<JsonObject> transactions, String tenantId) {
    throw new RuntimeException("buildUpdatePermanentTransactionQuery: not implemented in DefaultTransactionDAO");
  }
}
