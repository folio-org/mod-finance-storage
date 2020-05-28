package org.folio.service.transactions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

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
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.folio.service.summary.TransactionSummaryService;
import org.javamoney.moneta.Money;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;


public class EncumbranceAllOrNothingService extends BaseAllOrNothingTransactionService<OrderTransactionSummary> {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String SELECT_BUDGETS_BY_ORDER_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId "
    + "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?";

  public EncumbranceAllOrNothingService(BudgetService budgetService,
                                          TemporaryTransactionDAO temporaryTransactionDAO,
                                          LedgerFiscalYearService ledgerFiscalYearService,
                                          FundService fundService,
                                          TransactionSummaryService<OrderTransactionSummary> transactionSummaryService,
                                          TransactionDAO transactionsDAO,
                                        LedgerService ledgerService) {
    super(budgetService, temporaryTransactionDAO, ledgerFiscalYearService, fundService, transactionSummaryService, transactionsDAO, ledgerService);
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

    return existingTransactions.stream()
      .collect(groupingBy(
        transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

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
   * Calculates remaining amount for encumbrance
   * [remaining amount] = (allocated * allowableEncumbered) - (allocated - (unavailable + available)) - (encumbered + awaitingPayment + expenditures)
   *
   * @param budget     processed budget
   * @param currency   processed transaction currency
   * @return remaining amount for encumbrance
   */
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
    return tempGrouped.entrySet().stream()
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

}
