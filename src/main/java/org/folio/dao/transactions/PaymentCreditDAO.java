package org.folio.dao.transactions;

import static org.folio.dao.transactions.EncumbranceDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.List;

import org.folio.rest.persist.HelperUtils;

import io.vertx.core.json.JsonObject;

public class PaymentCreditDAO extends BaseTransactionDAO implements TransactionDAO {

  public static final String INSERT_PERMANENT_PAYMENTS_CREDITS = "INSERT INTO %s (id, jsonb) (SELECT id, jsonb - 'transactionSummaryId' FROM %s AS transactions WHERE transactionSummaryId = $1 " +
    "AND (transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')) ON CONFLICT DO NOTHING;";

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";

  @Override
  protected String createPermanentTransactionsQuery(String tenantId) {
    return String.format(INSERT_PERMANENT_PAYMENTS_CREDITS, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

  @Override
  protected String buildUpdatePermanentTransactionQuery(List<JsonObject> transactions, String tenantId) {
    return String.format("UPDATE %s AS transactions " +
      "SET jsonb = t.jsonb FROM (VALUES  %s) AS t (id, jsonb) " +
      "WHERE t.id::uuid = transactions.id;", getFullTableName(tenantId, TRANSACTIONS_TABLE), HelperUtils.getQueryValues(transactions));
  }

}
