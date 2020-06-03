package org.folio.service.transactions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.folio.service.summary.TransactionSummaryService;
import org.javamoney.moneta.Money;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class EncumbranceAllOrNothingService extends BaseAllOrNothingTransactionService<OrderTransactionSummary> {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String SELECT_BUDGETS_BY_ORDER_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
      + "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId "
      + "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?";

  public EncumbranceAllOrNothingService(BudgetService budgetService, TemporaryTransactionDAO temporaryTransactionDAO,
      LedgerFiscalYearService ledgerFiscalYearService, FundService fundService,
      TransactionSummaryService<OrderTransactionSummary> transactionSummaryService, TransactionDAO transactionsDAO,
      LedgerService ledgerService) {
    super(budgetService, temporaryTransactionDAO, ledgerFiscalYearService, fundService, transactionSummaryService, transactionsDAO,
        ledgerService);
  }

  @Override
  Void handleValidationError(Transaction transaction) {
    List<Error> errors = new ArrayList<>();

    errors.addAll(buildNullValidationError(getSummaryId(transaction), "encumbrance"));
    errors.addAll(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    MultiKeyMap<String, Budget> groupedBudgets = new MultiKeyMap<>();
    groupedBudgets.putAll(budgets.stream().collect(toMap(budget -> new MultiKey<>(budget.getFundId(), budget.getFiscalYearId()), identity())));

    return existingTransactions.stream().collect(groupingBy(transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

  }

  @Override
  protected String getSelectBudgetsQuery(String tenantId) {
    return getSelectBudgetsQuery(SELECT_BUDGETS_BY_ORDER_ID, tenantId, TEMPORARY_ORDER_TRANSACTIONS);
  }

  @Override
  protected boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget) {
    return ledger.getRestrictEncumbrance() && budget.getAllowableEncumbrance() != null;
  }

  /**
   * Calculates remaining amount for encumbrance [remaining amount] = (allocated * allowableEncumbered) - (allocated - (unavailable
   * + available)) - (encumbered + awaitingPayment + expenditures)
   *
   * @param budget   processed budget
   * @param currency processed transaction currency
   * @return remaining amount for encumbrance
   */
  @Override
  protected Money getBudgetRemainingAmount(Budget budget, String currency) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableEncumbered converted from percentage value
    double allowableEncumbered = Money.of(budget.getAllowableEncumbrance(), currency).divide(100d).getNumber().doubleValue();
    Money unavailable = Money.of(budget.getUnavailable(), currency);
    Money available = Money.of(budget.getAvailable(), currency);
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    Money expenditures = Money.of(budget.getExpenditures(), currency);

    Money result = allocated.multiply(allowableEncumbered);
    result = result.subtract(allocated.subtract(unavailable.add(available)));
    result = result.subtract(encumbered.add(awaitingPayment).add(expenditures));

    return result;
  }

  /**
   * To prevent partial encumbrance transactions for an order, all the encumbrances must be created following All or nothing
   */
  @Override
  Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    return transactionsDAO.saveTransactionsToPermanentTable(summaryId, client)
      .compose(updated -> {
        if (updated > 0) {
          return updateBudgetsLedgersTotals(transactions, client);
        }
        return Future.succeededFuture();
      });
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return Optional.ofNullable(transaction.getEncumbrance())
      .map(Encumbrance::getSourcePurchaseOrderId)
      .orElse(null);
  }

  private Future<Void> updateBudgetsLedgersTotals(List<Transaction> transactions, DBClient client) {
    String sql = getSelectBudgetsQuery(client.getTenantId());
    JsonArray params = new JsonArray();
    params.add(getSummaryId(transactions.get(0)));
    return budgetService.getBudgets(sql, params, client)
      .compose(oldBudgets -> {
        List<Budget> newBudgets = updateBudgetsTotals(transactions, oldBudgets);
        return budgetService.updateBatchBudgets(newBudgets, client)
          .compose(listTx -> updateLedgerFYsWithTotals(oldBudgets, newBudgets, client));
      });
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet()
      .stream()
      .map(this::updateBudgetTotals)
      .collect(toList());
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {

          double newEncumbered = sumMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency);
          budget.setEncumbered(newEncumbered);

          recalculateOverEncumbered(budget, currency);
          recalculateAvailableUnavailable(budget, currency);
        });
    }
    return budget;
  }

  private void recalculateOverEncumbered(Budget budget, CurrencyUnit currency) {
    double a = subtractMoneyNonNegative(budget.getAllocated(), budget.getExpenditures(), currency);
    a = subtractMoneyNonNegative(a, budget.getAwaitingPayment(), currency);
    double newOverEncumbrance = subtractMoneyNonNegative(budget.getEncumbered(), a, currency);
    budget.setOverEncumbrance(newOverEncumbrance);
  }

  private void recalculateAvailableUnavailable(Budget budget, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
        -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoneyNonNegative(budget.getAllocated(), newUnavailable, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
  }


  @Override
  public Future<Void> updateTransaction(String id, Transaction transaction, Context context, Map<String, String> okapiHeaders) {
    DBClient client = new DBClient(context, okapiHeaders);
    return isTransactionExists(id, client).compose(transactionExists -> {
      if (Boolean.TRUE.equals(transactionExists)) {
        return updateEncumbrance(transaction, context, okapiHeaders);
      } else {
        return createTransaction(transaction, context, okapiHeaders).mapEmpty();
      }
    });
  }

  public Future<Void> updateEncumbrance(Transaction transaction, Context context, Map<String, String> headers) {
    try {
      handleValidationError(transaction);
    } catch (HttpStatusException e) {
      return  Future.failedFuture(e);
    }
    DBClient client = new DBClient(context, headers);
    return verifyTransactionExistence(transaction.getId(), client)
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


    private Future<Boolean> isTransactionExists(String transactionId, DBClient client) {
    Promise<Boolean> promise = Promise.promise();
    client.getPgClient().getById(TRANSACTION_TABLE, transactionId, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(reply.result() != null);
      }
    });
    return promise.future();
  }

  private Future<Void> handleTransactionUpdate(List<Transaction> newTransactions, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    newTransactions.stream()
      .map(Transaction::getId)
      .forEach(id -> criterionBuilder.with("id", id));

    return transactionsDAO.getTransactions(criterionBuilder.build(), client)
      .compose(existingTransactions -> updateBudgetsTotals(existingTransactions, newTransactions, client)
        .map(v -> excludeReleasedEncumbrances(newTransactions, existingTransactions)))
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

  private Future<Void> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> newTransactions,
      DBClient client) {
    JsonArray params = new JsonArray();
    params.add(newTransactions.get(0).getEncumbrance().getSourcePurchaseOrderId());
    return budgetService.getBudgets(getSelectBudgetsQuery(client.getTenantId()), params, client)
      .compose(oldBudgets -> {
        List<Budget> newBudgets = getNewBudgetsTotals(existingTransactions, newTransactions, oldBudgets);

        return budgetService.updateBatchBudgets(newBudgets, client)
          .compose(integer -> updateLedgerFYsWithTotals(oldBudgets, newBudgets, client));
      });
  }


  private List<Budget> getNewBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> tempTransactions, List<Budget> budgets) {
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

    if (isEncumbrancePending(tmpTransaction)) {
      tmpTransaction.setAmount(0d);
      tmpTransaction.getEncumbrance().setInitialAmountEncumbered(0d);

    } else {
      // awaitingPayment increases by the same amount
      double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
      newAwaitingPayment = subtractMoneyNonNegative(newAwaitingPayment, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
      budget.setAwaitingPayment(newAwaitingPayment);

      // encumbrance transaction.amount is updated to (initial encumbrance - awaiting payment - expended)
      double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(),tmpTransaction.getEncumbrance().getAmountAwaitingPayment(),currency);
      newAmount = subtractMoney(newAmount, tmpTransaction.getEncumbrance().getAmountExpended(), currency);
      tmpTransaction.setAmount(newAmount);
    }

  }

  private boolean isEncumbranceReleased(Transaction transaction) {
    return transaction.getEncumbrance()
      .getStatus() == Encumbrance.Status.RELEASED;
  }

  private boolean isEncumbrancePending(Transaction transaction) {
    return transaction.getEncumbrance().getStatus() == Encumbrance.Status.PENDING;
  }

}
