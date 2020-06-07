package org.folio.dao.transactions;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.List;

import org.folio.rest.persist.HelperUtils;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EncumbranceDAO extends BaseTransactionDAO implements TransactionDAO {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";
  public static final String TRANSACTIONS_TABLE = "transaction";

  public static final String INSERT_PERMANENT_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) (SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ?) "
    + "ON CONFLICT DO NOTHING;";

  @Override
  protected String buildUpdatePermanentTransactionQuery(List<JsonObject> transactions, String tenantId) {
    return String.format("UPDATE %s AS transactions " +
      "SET jsonb = t.jsonb FROM (VALUES  %s) AS t (id, jsonb) " +
      "WHERE t.id::uuid = transactions.id;", getFullTableName(tenantId, TRANSACTIONS_TABLE), HelperUtils.getQueryValues(transactions));
  }

  @Override
  protected String createPermanentTransactionsQuery(String tenantId) {
    return String.format(INSERT_PERMANENT_ENCUMBRANCES, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_ORDER_TRANSACTIONS));
  }

  protected String createPermanentTransactionsQuery(String tenantId, List<String> ids) {
    return String.format(INSERT_PERMANENT_ENCUMBRANCES, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_ORDER_TRANSACTIONS));
  }

}
