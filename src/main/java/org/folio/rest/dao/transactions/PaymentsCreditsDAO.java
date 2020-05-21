package org.folio.rest.dao.transactions;

import static org.folio.rest.dao.transactions.EncumbrancesDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.List;

import org.folio.rest.persist.HelperUtils;

import io.vertx.core.json.JsonObject;

public class PaymentsCreditsDAO extends BaseTransactionsDAO implements TransactionsDAO {

  public static final String INSERT_PERMANENT_PAYMENTS_CREDITS = "INSERT INTO %s (id, jsonb) SELECT id, jsonb FROM %s WHERE sourceInvoiceId = ? AND jsonb ->> 'transactionType' != 'Encumbrance'"
    + "ON CONFLICT DO NOTHING;";

  public static final String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = ?";

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
