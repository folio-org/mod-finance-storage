package org.folio.rest.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.TransactionSummaryAPI.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.MoneyUtils;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

public class PaymentCreditHandler extends AllOrNothingHandler {

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";
  private static final String TRANSACTIONS_TABLE = "transaction";

  public PaymentCreditHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> asyncResultHandler) {
    super(TEMPORARY_INVOICE_TRANSACTIONS, INVOICE_TRANSACTION_SUMMARIES, okapiHeaders, ctx, asyncResultHandler);
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  @Override
  Criterion getTransactionBySummaryIdCriterion(String value) {
    return getCriterionByFieldNameAndValueNotJsonb("sourceInvoiceId", "=", value);
  }

  @Override
  public void createTransaction(Transaction transaction) {
    processAllOrNothing(transaction, this::processAllPaymentsCredits).setHandler(result -> {
      if (result.failed()) {
        HttpStatusException cause = (HttpStatusException) result.cause();
        if (cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
          getAsyncResultHandler().handle(Future.succeededFuture(
              FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond400WithTextPlain(cause.getPayload())));
        } else {
          getAsyncResultHandler().handle(Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse
            .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
        }
      } else {
        log.debug("Preparing response to client");
        getAsyncResultHandler().handle(
            Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond201WithApplicationJson(
                transaction, FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.headersFor201()
                  .withLocation(TRANSACTION_LOCATION_PREFIX + transaction.getId()))));
      }
    });

  }

  public Future<Tx<List<Transaction>>> processAllPaymentsCredits(Tx<List<Transaction>> tx) {
    return updateEncumbranceTotals(tx).compose(this::updateBudgetsTotals)
      .compose(this::createPermanentTransactions);

  }

  private Future<Tx<List<Transaction>>> updateBudgetsTotals(Tx<List<Transaction>> tx) {
    return getBudgets(tx).map(budgets -> budgets.stream()
      .collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedbudgets -> calculatePaymentBudgetsTotals(tx.getEntity(), groupedbudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(tx.getEntity(), grpBudgets))
      .map(grpBudgets -> calculateNonEncumbranceBudgetTotals(tx.getEntity(), grpBudgets))
      .compose(budgets -> updateBudgets(tx, budgets.values()
        .stream()
        .collect(Collectors.toList())));
  }

  private Map<String, Budget> calculatePaymentBudgetsTotals(List<Transaction> tempTransactions,
      Map<String, Budget> groupedBudgets) {
    // multi map grouped by FundId and FYId , so fetching budget is easier
    Map<Budget, List<Transaction>> paymentBudgetsGrouped = tempTransactions.stream()
      .filter(txn -> txn.getTransactionType()
        .equals(Transaction.TransactionType.PAYMENT) && txn.getPaymentEncumbranceId() != null)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getFromFundId())));

    paymentBudgetsGrouped.entrySet()
      .forEach(this::updateBudgetPaymentTotals);

    return groupedBudgets;

  }

  private Map<String, Budget> calculateCreditBudgetsTotals(List<Transaction> tempTransactions, Map<String, Budget> groupedBudgets) {
    // multi map grouped by FundId and FYId , so fetching budget is easier
    Map<Budget, List<Transaction>> creditBudgetsGrouped = tempTransactions.stream()
      .filter(txn -> txn.getTransactionType()
        .equals(Transaction.TransactionType.CREDIT) && txn.getPaymentEncumbranceId() != null)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getToFundId())));

    creditBudgetsGrouped.entrySet()
      .forEach(this::updateBudgetCreditTotals);

    return groupedBudgets;
  }

  private Map<String, Budget> calculateNonEncumbranceBudgetTotals(List<Transaction> tempTransactions,
      Map<String, Budget> groupedBudgets) {
    // multi map grouped by FundId and FYId , so fetching budget is easier
    Map<Budget, List<Transaction>> noEncumbranceBudgetsGrouped = tempTransactions.stream()
      .filter(txn -> txn.getPaymentEncumbranceId() == null)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getFromFundId())));

    noEncumbranceBudgetsGrouped.entrySet()
      .forEach(this::updateBudgetNoEncumbranceTotals);

    return groupedBudgets;

  }

  private Budget updateBudgetPaymentTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    // check is not empty
    entry.getValue()
      .stream()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.sumMoney(budget.getExpenditures(), txn.getAmount(), currency));
        budget.setAwaitingPayment(MoneyUtils.subtractMoney(budget.getAwaitingPayment(), txn.getAmount(), currency));
      });

    return budget;
  }

  private Budget updateBudgetCreditTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    // check is not empty
    entry.getValue()
      .stream()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.subtractMoney(budget.getExpenditures(), txn.getAmount(), currency));
        budget.setEncumbered(MoneyUtils.sumMoney(budget.getEncumbered(), txn.getAmount(), currency));
      });
    return budget;
  }

  private Budget updateBudgetNoEncumbranceTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    // check is not empty
    entry.getValue()
      .stream()
      .forEach(txn -> {
        if (txn.getTransactionType()
          .equals(Transaction.TransactionType.CREDIT)) {
          budget.setAvailable(MoneyUtils.sumMoney(budget.getAvailable(), txn.getAmount(), currency));
          budget.setUnavailable(MoneyUtils.subtractMoney(budget.getUnavailable(), txn.getAmount(), currency));
        } else if (txn.getTransactionType()
          .equals(Transaction.TransactionType.PAYMENT)) {
          budget.setAvailable(MoneyUtils.subtractMoney(budget.getAvailable(), txn.getAmount(), currency));
          budget.setUnavailable(MoneyUtils.sumMoney(budget.getUnavailable(), txn.getAmount(), currency));
        }

      });
    return budget;
  }

  private Future<Tx<List<Transaction>>> updateEncumbranceTotals(Tx<List<Transaction>> tx) {
    Boolean noEncumbrances = tx.getEntity()
      .stream()
      .allMatch(tx1 -> StringUtils.isBlank(tx1.getPaymentEncumbranceId()));

    if (noEncumbrances) {
      return Future.succeededFuture(tx);
    }

    return getAllEncumbrances(tx).map(encumbrances -> encumbrances.stream()
      .collect(toMap(Transaction::getId, Function.identity())))
      .map(encumbrancesMap -> applyPayments(tx.getEntity(), encumbrancesMap))
      .map(encumbrancesMap -> applyCredits(tx.getEntity(), encumbrancesMap))
      .map(map -> new Tx<>(map.values()
        .stream()
        .collect(Collectors.toList()), tx.getPgClient()).withConnection(tx.getConnection()))
      .compose(this::updatePermanentTransactions)
      .compose(ok -> Future.succeededFuture(tx));

  }

  private Map<String, Transaction> applyCredits(List<Transaction> tempTxns, Map<String, Transaction> encumbrancesMap) {

    List<Transaction> tempCredits = tempTxns.stream()
      .filter(txn -> txn.getTransactionType()
        .equals(Transaction.TransactionType.CREDIT))
      .collect(Collectors.toList());
    CurrencyUnit currency = Monetary.getCurrency(tempCredits.get(0)
      .getCurrency());
    tempCredits.forEach(creditTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(creditTxn.getPaymentEncumbranceId());
      double newAwaitingPayment = MoneyUtils.subtractMoney(encumbranceTxn.getEncumbrance()
        .getAmountAwaitingPayment(), creditTxn.getAmount(), currency);
      double newExpended = MoneyUtils.sumMoney(encumbranceTxn.getEncumbrance()
        .getAmountExpended(), creditTxn.getAmount(), currency);
      double newAmount = MoneyUtils.subtractMoney(encumbranceTxn.getAmount(), creditTxn.getAmount(), currency);

      encumbranceTxn.getEncumbrance()
        .setAmountAwaitingPayment(newAwaitingPayment);
      encumbranceTxn.getEncumbrance()
        .setAmountExpended(newExpended);
      encumbranceTxn.setAmount(newAmount);
    });

    return encumbrancesMap;
  }

  private Map<String, Transaction> applyPayments(List<Transaction> tempTxns, Map<String, Transaction> encumbrancesMap) {
    List<Transaction> tempPayments = tempTxns.stream()
      .filter(txn -> txn.getTransactionType()
        .equals(Transaction.TransactionType.PAYMENT))
      .collect(Collectors.toList());
    CurrencyUnit currency = Monetary.getCurrency(tempPayments.get(0)
      .getCurrency());
    tempPayments.forEach(pymtTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(pymtTxn.getPaymentEncumbranceId());
      double newExpended = MoneyUtils.subtractMoney(encumbranceTxn.getEncumbrance()
        .getAmountExpended(), pymtTxn.getAmount(), currency);
      double newAmount = MoneyUtils.sumMoney(encumbranceTxn.getAmount(), pymtTxn.getAmount(), currency);

      encumbranceTxn.getEncumbrance()
        .setAmountExpended(newExpended);
      encumbranceTxn.setAmount(newAmount);
    });

    return encumbrancesMap;
  }

  private Future<List<Transaction>> getAllEncumbrances(Tx<List<Transaction>> tx) {
    Promise<List<Transaction>> promise = Promise.promise();
    String sql = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb " + "FROM "
        + getFullTableName(getTenantId(), TRANSACTIONS_TABLE) + " AS permtransactions " + "INNER JOIN "
        + getFullTemporaryTransactionTableName() + " AS transactions "
        + "ON transactions.paymentEncumbranceId = permtransactions.id" + " WHERE transactions.sourceInvoiceId = ?";
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity()
      .get(0)));
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Transaction> encumbrances = reply.result()
            .getResults()
            .stream()
            .flatMap(JsonArray::stream)
            .map(o -> new JsonObject(o.toString()).mapTo(Transaction.class))
            .collect(Collectors.toList());
          promise.complete(encumbrances);
        }
      });
    return promise.future();
  }

  private Future<List<Budget>> getBudgets(Tx<List<Transaction>> tx) {
    Promise<List<Budget>> promise = Promise.promise();
    String sql = "SELECT DISTINCT ON (budgets.id) budgets.jsonb " + "FROM " + getFullTableName(getTenantId(), BUDGET_TABLE)
        + " AS budgets " + "INNER JOIN " + getFullTemporaryTransactionTableName() + " AS transactions "
        + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
        + "WHERE transactions.sourceInvoiceId = ?";
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity()
      .get(0)));
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = reply.result()
            .getResults()
            .stream()
            .flatMap(JsonArray::stream)
            .map(o -> new JsonObject(o.toString()).mapTo(Budget.class))
            .collect(Collectors.toList());
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  @Override
  public void updateTransaction(Transaction transaction) {
    throw new UnsupportedOperationException("Payments and credits are Immutable");
  }

  @Override
  void handleValidationError(Transaction transaction) {
    // TODO Auto-generated method stub
  }

  @Override
  String createTempTransactionQuery() {
    return "INSERT INTO " + getFullTemporaryTransactionTableName() + " (id, jsonb) VALUES (?, ?::JSON) ";
  }

  @Override
  String createPermanentTransactionsQuery() {
    return String.format("INSERT INTO %s (id, jsonb) " + "SELECT id, jsonb FROM %s WHERE sourceInvoiceId = ? "
        + "ON CONFLICT DO NOTHING;", getFullTransactionTableName(), getTemporaryTransactionTable());

  }

  @Override
  int getSummaryCount(JsonObject summary){
    return summary.getInteger("numPaymentsCredits");
  }

}
