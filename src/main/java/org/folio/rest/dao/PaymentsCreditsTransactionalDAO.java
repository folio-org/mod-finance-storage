package org.folio.rest.dao;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Tx;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;

import static java.util.stream.Collectors.toMap;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.PostgresClient.pojo2json;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class PaymentsCreditsTransactionalDAO implements TransactionsTransactionalDAO {


  public static final String INSERT_PERMANENT_PAYMENTS_CREDITS = "INSERT INTO %s (id, jsonb) SELECT id, jsonb FROM %s WHERE sourceInvoiceId = ? AND jsonb ->> 'transactionType' != 'Encumbrance'"
    + "ON CONFLICT DO NOTHING;";

  public static final String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = ?";

  public static final String SELECT_LEDGERFYS_TRANSACTIONS = "SELECT ledger_fy.jsonb, transactions.jsonb FROM %s AS ledger_fy INNER JOIN %s AS funds"
    + " ON (funds.ledgerId = ledger_fy.ledgerId) INNER JOIN %s AS transactions"
    + " ON (funds.id = transactions.fromFundId OR funds.id = transactions.toFundId)"
    + " WHERE (transactions.sourceInvoiceId = ? AND ledger_fy.fiscalYearId = transactions.fiscalYearId AND transactions.paymentEncumbranceId IS NULL);";

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";

  @Override
  public Future<List<Transaction>> getTransactionsBySummaryId(String summaryId, Tx tx) {
    return null;
  }

  @Override
  public Future<Integer> saveTransactionsToPermanentTable(String summaryId, Tx tx) {
    return null;
  }

  @Override
  public Future<Integer> deleteTempTransactionsBySummaryId(String summaryId, Tx<List<Transaction>> tx) {
    return null;
  }

  private Future<Map<LedgerFY, List<Transaction>>> groupTempTransactionsByLedgerFy(String summaryId, Tx<List<Transaction>> tx) {
    Promise<Map<LedgerFY, List<Transaction>>> promise = Promise.promise();
    String sql = getLedgerFYsQuery(tx.getTenantId());
    JsonArray params = new JsonArray();
    params.add(summaryId);
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          Map<LedgerFY, List<Transaction>> ledgers = reply.result()
            .getResults()
            .stream()
            .collect(ledgerFYTransactionsMapping());
          promise.complete(ledgers);
        }
      });
    return promise.future();
  }

  public Collector<JsonArray, ?, HashMap<LedgerFY, List<Transaction>>> ledgerFYTransactionsMapping() {
    int ledgerFYColumnNumber = 0;
    int transactionsColumnNumber = 1;
    return toMap(o -> new JsonObject(o.getString(ledgerFYColumnNumber))
      .mapTo(LedgerFY.class), o -> Collections.singletonList(new JsonObject(o.getString(transactionsColumnNumber))
      .mapTo(Transaction.class)), (o, o2) -> {
      List<Transaction> newList = new ArrayList<>(o); newList.addAll(o2); return newList;}, HashMap::new);
  }

  private String getLedgerFYsQuery(String tenantId) {
    return String.format(SELECT_LEDGERFYS_TRANSACTIONS,
      getFullTableName(tenantId, LEDGERFY_TABLE), getFullTableName(tenantId, FUND_TABLE),
      getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

}
