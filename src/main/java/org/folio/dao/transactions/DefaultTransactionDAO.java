package org.folio.dao.transactions;

import io.vertx.core.json.JsonObject;

import java.util.List;

public class DefaultTransactionDAO extends BaseTransactionDAO {
  @Override
  protected String createPermanentTransactionsQuery(String tenantId) {
    return null;
  }

  @Override
  protected String buildUpdatePermanentTransactionQuery(List<JsonObject> transactions, String tenantId) {
    return null;
  }
}
