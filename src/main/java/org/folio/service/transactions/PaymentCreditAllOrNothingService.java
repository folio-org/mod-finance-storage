package org.folio.service.transactions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.lang3.StringUtils;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.MoneyUtils;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.folio.service.summary.TransactionSummaryService;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class PaymentCreditAllOrNothingService extends BaseAllOrNothingTransactionService<InvoiceTransactionSummary> {

  private static final String TRANSACTIONS_TABLE = "transaction";

  public static final String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = ? AND (transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')";

  public static final String SELECT_PERMANENT_TRANSACTIONS = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb FROM %s AS permtransactions INNER JOIN %s AS transactions "
    + "ON transactions.paymentEncumbranceId = permtransactions.id WHERE transactions.sourceInvoiceId = ? AND (transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')";

  Predicate<Transaction> hasEncumbrance = txn -> txn.getPaymentEncumbranceId() != null;
  Predicate<Transaction> isPaymentTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.PAYMENT);
  Predicate<Transaction> isCreditTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.CREDIT);

  public PaymentCreditAllOrNothingService(BudgetService budgetService,
                                           TemporaryTransactionDAO temporaryTransactionDAO,
                                           LedgerFiscalYearService ledgerFiscalYearService,
                                           FundService fundService,
                                           TransactionSummaryService<InvoiceTransactionSummary> transactionSummaryService,
                                           TransactionDAO transactionsDAO,
                                          LedgerService ledgerService) {
    super(budgetService, temporaryTransactionDAO, ledgerFiscalYearService, fundService, transactionSummaryService, transactionsDAO, ledgerService);
  }

  @Override
  public Future<Void> updateTransaction(String id, Transaction transaction, Context context, Map<String, String> headers) {
    try {
      handleValidationError(transaction);
    } catch (HttpStatusException e) {
      return  Future.failedFuture(e);
    }
    DBClient client = new DBClient(context, headers);
    return verifyTransactionExistence(id, client)
      .compose(v -> transactionSummaryService.getAndCheckTransactionSummary(transaction, client)
        .compose(summary -> collectTempTransactions(transaction, client)
          .compose(transactions -> {
            if (transactions.size() == transactionSummaryService.getNumTransactions(summary)) {
              return client.startTx()
                .compose(c -> handleTransactionUpdate(transactions, client))
                .compose(vVoid -> finishAllOrNothing(summary, client))
                .compose(vVoid -> client.endTx())
                .onComplete(result -> {
                  if (result.failed()) {
                    log.error("Transactions or associated data failed to be processed", result.cause());
                    client.rollbackTransaction();
                  } else {
                    log.info("Transactions and associated data were successfully processed");
                  }
                });

            } else {
              return Future.succeededFuture();
            }
          }))
      );
  }

  private Future<Void> handleTransactionUpdate(List<Transaction> newTransactions, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    newTransactions.stream()
      .map(Transaction::getId)
      .forEach(id -> criterionBuilder.with("id", id));

    return transactionsDAO.getTransactions(criterionBuilder.build(), client)
      .compose(existingTransactions -> updateBudgetsTotals(existingTransactions, newTransactions, client)
        .map(listTx -> excludeReleasedEncumbrances(newTransactions, existingTransactions)))
      .compose(transactions -> transactionsDAO.updatePermanentTransactions(transactions, client));
  }

  private Future<Void> verifyTransactionExistence(String transactionId, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    criterionBuilder.with("id", transactionId);
    return transactionsDAO.getTransactions(criterionBuilder.build(), client)
      .map(transactions -> {
        if (transactions.isEmpty()) {
          throw new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "Not found");
        }
        return null;
      });
  }

  private List<Transaction> excludeReleasedEncumbrances(List<Transaction> newTransactions, List<Transaction> existingTransactions) {
    Map<String, Transaction> groupedTransactions = existingTransactions.stream().collect(toMap(Transaction::getId, identity()));
    return newTransactions.stream()
      .filter(transaction -> groupedTransactions.get(transaction.getId()).getEncumbrance().getStatus() != Encumbrance.Status.RELEASED)
      .collect(Collectors.toList());
  }

  private Future<Integer> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> newTransactions, DBClient client) {
    JsonArray params = new JsonArray();
    params.add(getSummaryId(newTransactions.get(0)));
    return budgetService.getBudgets(getSelectBudgetsQuery(client.getTenantId()), params, client)
      .map(budgets -> updateBudgetsTotals(existingTransactions, newTransactions, budgets))
      .compose(budgets -> budgetService.updateBatchBudgets(budgets, client));
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<String, Transaction> existingGrouped = existingTransactions.stream().collect(toMap(Transaction::getId, identity()));
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(listEntry -> updateBudgetTotals(listEntry, existingGrouped))
      .collect(Collectors.toList());
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry, Map<String, Transaction> existingGrouped) {
    Budget budget = entry.getKey();

    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          Transaction existingTransaction = existingGrouped.get(tmpTransaction.getId());
          if (!isEncumbranceReleased(existingTransaction)) {
            processBudget(budget, currency, tmpTransaction, existingTransaction);
            if (isEncumbranceReleased(tmpTransaction)) {
              releaseEncumbrance(budget, currency, tmpTransaction);
            }
          }
        });
    }
    return budget;
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    MultiKeyMap<String, Budget> groupedBudgets = new MultiKeyMap<>();
    groupedBudgets.putAll(budgets.stream().collect(toMap(budget -> new MultiKey<>(budget.getFundId(), budget.getFiscalYearId()), identity())));

    return existingTransactions.stream()
      .collect(groupingBy(
        transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

  }

  private void releaseEncumbrance(Budget budget, CurrencyUnit currency, Transaction tmpTransaction) {
    //encumbered decreases by the amount being released
    budget.setEncumbered(subtractMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency));

    // available increases by the amount being released
    budget.setAvailable(sumMoney(budget.getAvailable(), tmpTransaction.getAmount(), currency));

    // unavailable decreases by the amount being released (min 0)
    double newUnavailable = subtractMoney(budget.getUnavailable(), tmpTransaction.getAmount(), currency);
    budget.setUnavailable(newUnavailable < 0 ? 0 : newUnavailable);

    // transaction.amount becomes 0 (save the original value for updating the budget)
    tmpTransaction.setAmount(0d);
  }

  private void processBudget(Budget budget, CurrencyUnit currency, Transaction tmpTransaction, Transaction existingTransaction) {
    // encumbered decreases by the difference between provided and previous transaction.encumbrance.amountAwaitingPayment values
    double newEncumbered = subtractMoney(budget.getEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newEncumbered = sumMoney(newEncumbered, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    budget.setEncumbered(newEncumbered);

    // awaitingPayment increases by the same amount
    double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newAwaitingPayment = subtractMoneyNonNegative(newAwaitingPayment, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    budget.setAwaitingPayment(newAwaitingPayment);

    // encumbrance transaction.amount is updated to (initial encumbrance - awaiting payment - expended)
    double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newAmount = subtractMoney(newAmount, tmpTransaction.getEncumbrance().getAmountExpended(), currency);
    tmpTransaction.setAmount(newAmount);
  }

  private boolean isEncumbranceReleased(Transaction transaction) {
    return transaction.getEncumbrance()
      .getStatus() == Encumbrance.Status.RELEASED;
  }

  /**
   * Before the temporary transaction can be moved to permanent transaction table, the corresponding fields in encumbrances and
   * budgets for payments/credits must be recalculated. To avoid partial transactions, all payments and credits will be done in a
   * database transaction
   *
   * @param client
   */
  @Override
  public Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    return updateEncumbranceTotals(transactions, client)
      .compose(dbc -> updateBudgetsTotals(transactions, client))
      .compose(dbc -> transactionsDAO.saveTransactionsToPermanentTable(summaryId, client))
      .mapEmpty();
  }

  /**
   * Update the Encumbrance transaction attached to the payment/Credit(from paymentEncumbranceID)
   * in a transaction
   *
   * @param transactions
   * @param client : the list of payments and credits
   */
  private Future<DBClient> updateEncumbranceTotals(List<Transaction> transactions, DBClient client) {
    boolean noEncumbrances = transactions
      .stream()
      .allMatch(transaction -> StringUtils.isBlank(transaction.getPaymentEncumbranceId()));

    if (noEncumbrances) {
      return Future.succeededFuture(client);
    }
    String summaryId = getSummaryId(transactions.get(0));
    return getAllEncumbrances(summaryId, client).map(encumbrances -> encumbrances.stream()
      .collect(toMap(Transaction::getId, identity())))
      .map(encumbrancesMap -> applyPayments(transactions, encumbrancesMap))
      .map(encumbrancesMap -> applyCredits(transactions, encumbrancesMap))
      //update all the re-calculated encumbrances into the Transaction table
      .map(map -> map.values()
        .stream()
        .collect(Collectors.toList()))
      .compose(trns -> transactionsDAO.updatePermanentTransactions(trns, client))
      .compose(ok -> Future.succeededFuture(client));

  }

  private Future<Integer> updateBudgetsTotals(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    JsonArray params = new JsonArray();
    params.add(summaryId);
    String sql = getSelectBudgetsQuery(client.getTenantId());
    return budgetService.getBudgets(sql, params, client)
      .map(budgets -> budgets.stream().collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedBudgets -> calculatePaymentBudgetsTotals(transactions, groupedBudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(transactions, grpBudgets))
      .compose(grpBudgets -> budgetService.updateBatchBudgets(grpBudgets.values(), client));
  }

  private Map<String, Budget> calculatePaymentBudgetsTotals(List<Transaction> tempTransactions,
      Map<String, Budget> groupedBudgets) {
    Map<Budget, List<Transaction>> paymentBudgetsGrouped = tempTransactions.stream()
      .filter(isPaymentTransaction)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getFromFundId())));

    if (!paymentBudgetsGrouped.isEmpty()) {
      log.debug("Calculating budget totals for payment transactions");
      paymentBudgetsGrouped.entrySet()
        .forEach(this::updateBudgetPaymentTotals);
    }

    return groupedBudgets;
  }

  private Map<String, Budget> calculateCreditBudgetsTotals(List<Transaction> tempTransactions, Map<String, Budget> groupedBudgets) {
    Map<Budget, List<Transaction>> creditBudgetsGrouped = tempTransactions.stream()
      .filter(isCreditTransaction)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getToFundId())));

    if (!creditBudgetsGrouped.isEmpty()) {
      log.debug("Calculating budget totals for credit transactions");
      creditBudgetsGrouped.entrySet()
        .forEach(this::updateBudgetCreditTotals);
    }

    return groupedBudgets;
  }

  private void updateBudgetPaymentTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.sumMoney(budget.getExpenditures(), txn.getAmount(), currency));
        double newAwaitingPayment = MoneyUtils.subtractMoney(budget.getAwaitingPayment(), txn.getAmount(), currency);
        budget.setAwaitingPayment(newAwaitingPayment);
      });
  }

  private void updateBudgetCreditTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.subtractMoney(budget.getExpenditures(), txn.getAmount(), currency));
        budget.setAwaitingPayment(MoneyUtils.sumMoney(budget.getAwaitingPayment(), txn.getAmount(), currency));
      });
  }

  /**
   * <pre>
   * Encumbrances are recalculated with the credit transaction as below:
   * - expended decreases by credit transaction amount (min 0)
   * </pre>
   */
  private Map<String, Transaction> applyCredits(List<Transaction> tempTxns, Map<String, Transaction> encumbrancesMap) {

    List<Transaction> tempCredits = tempTxns.stream()
      .filter(isCreditTransaction.and(hasEncumbrance))
      .collect(Collectors.toList());

    if (tempCredits.isEmpty()) {
      return encumbrancesMap;
    }
    CurrencyUnit currency = Monetary.getCurrency(tempCredits.get(0)
      .getCurrency());
    tempCredits.forEach(creditTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(creditTxn.getPaymentEncumbranceId());

      double newExpended = MoneyUtils.subtractMoneyNonNegative(encumbranceTxn.getEncumbrance()
        .getAmountExpended(), creditTxn.getAmount(), currency);
      encumbranceTxn.getEncumbrance()
        .setAmountExpended(newExpended);

    });

    return encumbrancesMap;
  }

  /**
   * <pre>
   * Encumbrances are recalculated with the payment transaction as below:
   * - expended increases by payment transaction amount
   * </pre>
   */
  private Map<String, Transaction> applyPayments(List<Transaction> tempTxns, Map<String, Transaction> encumbrancesMap) {
    List<Transaction> tempPayments = tempTxns.stream()
      .filter(isPaymentTransaction.and(hasEncumbrance))
      .collect(Collectors.toList());
    if (tempPayments.isEmpty()) {
      return encumbrancesMap;
    }

    CurrencyUnit currency = Monetary.getCurrency(tempPayments.get(0)
      .getCurrency());
    tempPayments.forEach(pymtTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(pymtTxn.getPaymentEncumbranceId());
      double newExpended = MoneyUtils.sumMoney(encumbranceTxn.getEncumbrance()
        .getAmountExpended(), pymtTxn.getAmount(), currency);

      encumbranceTxn.getEncumbrance()
        .setAmountExpended(newExpended);

    });

    return encumbrancesMap;
  }

  private Future<List<Transaction>> getAllEncumbrances(String summaryId, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    String sql = buildGetPermanentEncumbrancesQuery(client.getTenantId());
    JsonArray params = new JsonArray();
    params.add(summaryId);
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
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

  private String buildGetPermanentEncumbrancesQuery(String tenantId) {
    return String.format(SELECT_PERMANENT_TRANSACTIONS, getFullTableName(tenantId, TRANSACTIONS_TABLE),
        getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

  @Override
  Void handleValidationError(Transaction transaction) {

    List<Error> errors = new ArrayList<>();

    if (isCreditTransaction.test(transaction)) {
      errors.addAll(buildNullValidationError(transaction.getToFundId(), "toFundId"));
    } else {
      errors.addAll(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));
    }
    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }

  @Override
  protected String getSelectBudgetsQuery(String tenantId){
    return getSelectBudgetsQuery(SELECT_BUDGETS_BY_INVOICE_ID, tenantId, TEMPORARY_INVOICE_TRANSACTIONS);
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  @Override
  protected boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget) {
    return ledger.getRestrictExpenditures()
      && budget.getAllowableExpenditure() != null;
  }

}
