package org.folio.dao.transactions;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.HelperUtils;

import io.vertx.core.json.JsonObject;

public class EncumbranceDAO extends BaseTransactionDAO implements TransactionDAO {

  protected final Logger logger = LogManager.getLogger(this.getClass());

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";
  public static final String TRANSACTIONS_TABLE = "transaction";

  public static final String INSERT_PERMANENT_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) (SELECT id, jsonb - 'transactionSummaryId' FROM %s WHERE transactionSummaryId = $1) "
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

  @Override
  protected String createPermanentTransactionsQuery(String tenantId, List<String> ids) {
    String idsAsString = ids.stream()
      .map(id -> StringUtils.wrap(id, "'"))
      .collect(Collectors.joining(","));
    return String.format(INSERT_PERMANENT_TRANSACTIONS_BY_IDS, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_ORDER_TRANSACTIONS), idsAsString);
  }

}
