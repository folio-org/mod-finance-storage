package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.calculation.CalculationService;
import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.money.Monetary.getDefaultRounding;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.sumMoney;

public class PendingPaymentService implements TransactionManagingStrategy {

  public static final String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON (budgets.fundId = transactions.fromFundId  AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = $1 AND transactions.jsonb ->> 'transactionType' = 'Pending payment'";

  private final AllOrNothingTransactionService allOrNothingPendingPaymentService;
  private final TransactionDAO transactionsDAO;
  private final BudgetService budgetService;
  private final CalculationService calculationService;

  public PendingPaymentService(AllOrNothingTransactionService allOrNothingPendingPaymentService,
                               TransactionDAO transactionsDAO,
                               BudgetService budgetService,
                               CalculationService calculationService) {
    this.allOrNothingPendingPaymentService = allOrNothingPendingPaymentService;
    this.transactionsDAO = transactionsDAO;
    this.budgetService = budgetService;
    this.calculationService = calculationService;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, RequestContext requestContext) {
    DBClient dbClient = new DBClient(requestContext);
    return allOrNothingPendingPaymentService.createTransaction(transaction, dbClient, this::createTransactions);
  }

  @Override
  public Future<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    DBClient dbClient = new DBClient(requestContext);
    return allOrNothingPendingPaymentService.updateTransaction(transaction, dbClient, this::updateTransactions);
  }

  @Override
  public Transaction.TransactionType getStrategyName() {
    return PENDING_PAYMENT;
  }

  public Future<Void> createTransactions(List<Transaction> transactions, DBClient client) {
    List<Transaction> linkedToEncumbrance = transactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getAwaitingPayment()) && Objects.nonNull(transaction.getAwaitingPayment().getEncumbranceId()))
      .collect(Collectors.toList());
    List<Transaction> notLinkedToEncumbrance = transactions.stream()
      .filter(transaction -> Objects.isNull(transaction.getAwaitingPayment()) || Objects.isNull(transaction.getAwaitingPayment().getEncumbranceId()))
      .collect(Collectors.toList());

    String summaryId = getSummaryId(transactions.get(0));

    return budgetService.getBudgets(getSelectBudgetsQuery(client.getTenantId()), Tuple.of(UUID.fromString(summaryId)), client)
      .compose(oldBudgets -> processLinkedPendingPayments(linkedToEncumbrance, oldBudgets, client)
      .map(budgets -> processNotLinkedPendingPayments(notLinkedToEncumbrance, budgets))
        .map(this::makeAvailableUnavailableNonNegative)
        .compose(newBudgets -> budgetService.updateBatchBudgets(newBudgets, client)
          .compose(integer -> calculationService.updateLedgerFYsWithTotals(oldBudgets, newBudgets, client))))
      .compose(aVoid -> transactionsDAO.saveTransactionsToPermanentTable(transactions.get(0).getSourceInvoiceId(), client))
      .mapEmpty();
  }

  public Future<Void> updateTransactions(List<Transaction> transactions, DBClient dbClient) {
    return Future.succeededFuture();
  }

  private List<Budget> makeAvailableUnavailableNonNegative(List<Budget> budgets) {
    budgets.forEach(budget -> {
        budget.setAvailable(max(0, budget.getAvailable()));
        budget.setUnavailable(max(0, budget.getUnavailable()));
      });
    return budgets;
  }

  private List<Budget> processNotLinkedPendingPayments(List<Transaction> pendingPayments, List<Budget> oldBudgets) {
    if (isNotEmpty(pendingPayments)) {
      return updateBudgetsTotalsWithNotLinkedPendingPayments(pendingPayments, oldBudgets);
    }
    return oldBudgets;
  }

  private List<Budget> updateBudgetsTotalsWithNotLinkedPendingPayments(List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(this::updateBudgetTotalsWithNotLinkedPendingPayments)
      .collect(toList());
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    Map<String, List<Transaction>> groupedTransactions = existingTransactions.stream().collect(groupingBy(Transaction::getFromFundId));

    return budgets.stream()
      .collect(toMap(identity(), budget ->  groupedTransactions.getOrDefault(budget.getFundId(), Collections.emptyList())));

  }

  private Budget updateBudgetTotalsWithNotLinkedPendingPayments(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getAmount(), currency);
          budget.setAwaitingPayment(newAwaitingPayment);
          recalculateAvailableUnavailable(budget, tmpTransaction.getAmount(), currency);
        });
    }
    return budget;
  }

  private void recalculateAvailableUnavailable(Budget budget, Double transactionAmount, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoney(budget.getAvailable(), transactionAmount, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
  }


  private Future<List<Budget>> processLinkedPendingPayments(List<Transaction> pendingPayments, List<Budget> oldBudgets, DBClient client) {
    if (isNotEmpty(pendingPayments)) {

      CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
      pendingPayments.stream()
        .map(transaction -> transaction.getAwaitingPayment().getEncumbranceId())
        .forEach(id -> criterionBuilder.with("id", id));

      return transactionsDAO.getTransactions(criterionBuilder.build(), client)
        .map(encumbrances -> updateEncumbrancesTotals(encumbrances, pendingPayments))
        .compose(encumbrances -> {
          List<Budget> newBudgets = updateBudgetsTotalsWithLinkedPendingPayments(pendingPayments, encumbrances, oldBudgets);
          return transactionsDAO.updatePermanentTransactions(encumbrances, client)
            .map(newBudgets);
        });
    }
    return Future.succeededFuture(oldBudgets);
  }

  private List<Transaction> updateEncumbrancesTotals(List<Transaction> encumbrances, List<Transaction> pendingPayments) {
    Map<String, List<Transaction>> pendingPaymentsByEncumbranceId = pendingPayments.stream()
      .collect(groupingBy(transaction -> transaction.getAwaitingPayment().getEncumbranceId()));
    encumbrances.forEach(encumbrance -> updateEncumbranceTotals(encumbrance, pendingPaymentsByEncumbranceId.get(encumbrance.getId())));
    return encumbrances;
  }

  private void updateEncumbranceTotals(Transaction encumbrance, List<Transaction> transactions) {
    if (transactions.stream().anyMatch(transaction -> transaction.getAwaitingPayment().getReleaseEncumbrance())) {
      encumbrance.getEncumbrance().setStatus(Encumbrance.Status.RELEASED);
    }
    MonetaryAmount ppAmountTotal = transactions.stream()
      .map(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()))
      .reduce(Money::add).orElse(Money.zero(Monetary.getCurrency(encumbrance.getCurrency())));
    MonetaryAmount amount = Money.of(encumbrance.getAmount(), encumbrance.getCurrency()).subtract(ppAmountTotal);

    MonetaryAmount awaitingPayment = Money.of(encumbrance.getEncumbrance().getAmountAwaitingPayment(), encumbrance.getCurrency()).add(ppAmountTotal);

    encumbrance.setAmount(amount.with(getDefaultRounding()).getNumber().doubleValue());
    encumbrance.getEncumbrance().setAmountAwaitingPayment(awaitingPayment.with(getDefaultRounding()).getNumber().doubleValue());
  }

  private List<Budget> updateBudgetsTotalsWithLinkedPendingPayments(List<Transaction> pendingPayments, List<Transaction> encumbrances, List<Budget> budgets) {

    Map<String, Budget> fundIdBudgetMap = budgets.stream().collect(toMap(Budget::getFundId, budget -> JsonObject.mapFrom(budget).mapTo(Budget.class)));

    List<Transaction> releasedEncumbrances = encumbrances.stream()
      .filter(transaction -> transaction.getEncumbrance().getStatus() == Encumbrance.Status.RELEASED)
      .collect(Collectors.toList());

    List<Transaction> negativeEncumbrances = encumbrances.stream()
      .filter(transaction -> transaction.getAmount() < 0)
      .collect(Collectors.toList());

    applyNegativeEncumbrances(negativeEncumbrances, fundIdBudgetMap);
    applyPendingPayments(pendingPayments, fundIdBudgetMap);
    applyEncumbrances(releasedEncumbrances, fundIdBudgetMap);

    return new ArrayList<>(fundIdBudgetMap.values());
  }

  private void applyNegativeEncumbrances(List<Transaction> negativeEncumbrances, Map<String, Budget> fundIdBudgetMap) {
    if (isNotEmpty(negativeEncumbrances)) {
      CurrencyUnit currency = Monetary.getCurrency(negativeEncumbrances.get(0).getCurrency());
      negativeEncumbrances.forEach(transaction -> {
        Budget budget = fundIdBudgetMap.get(transaction.getFromFundId());
        MonetaryAmount amount = Money.of(transaction.getAmount(), currency).negate();

        MonetaryAmount available = Money.of(budget.getAvailable(), currency);
        MonetaryAmount unavailable = Money.of(budget.getUnavailable(), currency);
        MonetaryAmount encumbered = Money.of(budget.getEncumbered(), currency);

        double newAvailable = available.subtract(amount).getNumber().doubleValue();
        double newUnavailable = unavailable.add(amount).getNumber().doubleValue();

        budget.setAvailable(newAvailable);
        budget.setUnavailable(newUnavailable);
        budget.setEncumbered(encumbered.add(amount).getNumber().doubleValue());

        transaction.setAmount(0.00);
      });
    }
  }

  private void applyPendingPayments(List<Transaction> pendingPayments, Map<String, Budget> fundIdBudgetMap) {
    CurrencyUnit currency = Monetary.getCurrency(pendingPayments.get(0).getCurrency());

     pendingPayments.forEach(transaction -> {
       Budget budget = fundIdBudgetMap.get(transaction.getFromFundId());
       MonetaryAmount amount = Money.of(transaction.getAmount(), currency);
       MonetaryAmount encumbered = Money.of(budget.getEncumbered(), currency);
       MonetaryAmount awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
       double newEncumbered = encumbered.subtract(amount).getNumber().doubleValue();
       double newAwaitingPayment = awaitingPayment.add(amount).getNumber().doubleValue();
       budget.setEncumbered(newEncumbered);
       budget.setAwaitingPayment(newAwaitingPayment);
     });
  }

  private void applyEncumbrances(List<Transaction> releasedEncumbrances, Map<String, Budget> fundIdBudgetMap) {
    if (isNotEmpty(releasedEncumbrances)) {
      CurrencyUnit currency = Monetary.getCurrency(releasedEncumbrances.get(0).getCurrency());
      releasedEncumbrances.forEach(transaction -> {
        Budget budget = fundIdBudgetMap.get(transaction.getFromFundId());
        MonetaryAmount amount = Money.of(transaction.getAmount(), currency);
        MonetaryAmount encumbered = Money.of(budget.getEncumbered(), currency);
        double newEncumbered = encumbered.subtract(amount).getNumber().doubleValue();
        budget.setEncumbered(newEncumbered);
        recalculateAvailableUnavailable(budget, -transaction.getAmount(), currency);
        transaction.setAmount(0.00);
      });
    }

  }

  public String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  private String getSelectBudgetsQuery(String tenantId) {
    return String.format(SELECT_BUDGETS_BY_INVOICE_ID, getFullTableName(tenantId, BUDGET_TABLE), getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

}
