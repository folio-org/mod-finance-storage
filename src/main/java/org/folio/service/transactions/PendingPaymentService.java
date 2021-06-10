package org.folio.service.transactions;

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
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.javamoney.moneta.Money;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.javamoney.moneta.function.MonetaryFunctions;

public class PendingPaymentService implements TransactionManagingStrategy {

  public static final String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON (budgets.fundId = transactions.fromFundId  AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = $1 AND transactions.jsonb ->> 'transactionType' = 'Pending payment'";

  private final AllOrNothingTransactionService allOrNothingPendingPaymentService;
  private final TransactionDAO transactionsDAO;
  private final BudgetService budgetService;

  public PendingPaymentService(AllOrNothingTransactionService allOrNothingPendingPaymentService,
                               TransactionDAO transactionsDAO,
                               BudgetService budgetService) {
    this.allOrNothingPendingPaymentService = allOrNothingPendingPaymentService;
    this.transactionsDAO = transactionsDAO;
    this.budgetService = budgetService;
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

    return processPendingPayments(transactions, client)
      .compose(aVoid -> transactionsDAO.saveTransactionsToPermanentTable(transactions.get(0).getSourceInvoiceId(), client))
      .mapEmpty();
  }

  public Future<Void> updateTransactions(List<Transaction> tmpTransactions, DBClient client) {

    return getTransactions(tmpTransactions, client)
      .map(transactionsFromDB -> createDifferenceTransactions(tmpTransactions, transactionsFromDB))
      .compose(transactions -> processPendingPayments(transactions, client))
      .compose(transactions -> transactionsDAO.updatePermanentTransactions(tmpTransactions, client));
  }

  private Future<List<Transaction>> processPendingPayments(List<Transaction> transactions, DBClient client) {
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
        .compose(newBudgets -> budgetService.updateBatchBudgets(newBudgets, client)))
      .map(transactions);
  }

  private List<Transaction> createDifferenceTransactions(List<Transaction> tmpTransactions, List<Transaction> transactionsFromDB) {
    Map<String, Double> amountIdMap = tmpTransactions.stream().collect(toMap(Transaction::getId, Transaction::getAmount));
    return transactionsFromDB.stream()
      .map(transaction -> createDifferenceTransaction(transaction, amountIdMap.getOrDefault(transaction.getId(), 0d)))
      .collect(Collectors.toList());
  }

  private Transaction createDifferenceTransaction(Transaction transaction, double newAmount) {
    Transaction transactionDifference = JsonObject.mapFrom(transaction).mapTo(Transaction.class);
    CurrencyUnit currency = Monetary.getCurrency(transaction.getCurrency());
    double amountDifference = subtractMoney(newAmount, transaction.getAmount(), currency);
    return transactionDifference.withAmount(amountDifference);
  }


  private Future<List<Transaction>> getTransactions(List<Transaction> tmpTransactions, DBClient client) {

    List<String> ids = tmpTransactions.stream()
      .map(Transaction::getId)
      .collect(toList());

    return transactionsDAO.getTransactions(ids, client);
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
          budgetService.updateBudgetMetadata(budget, tmpTransaction);
          budgetService.clearReadOnlyFields(budget);
        });
    }
    return budget;
  }

  private Future<List<Budget>> processLinkedPendingPayments(List<Transaction> pendingPayments, List<Budget> oldBudgets, DBClient client) {
    if (isNotEmpty(pendingPayments)) {
      List<String> ids = pendingPayments.stream()
        .map(transaction -> transaction.getAwaitingPayment().getEncumbranceId())
        .collect(toList());

      return transactionsDAO.getTransactions(ids, client)
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
    MonetaryAmount ppAmountTotal = transactions.stream()
      .map(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()))
      .reduce(Money::add)
      .orElse(Money.zero(Monetary.getCurrency(encumbrance.getCurrency())));


    if (transactions.stream().anyMatch(transaction -> transaction.getAwaitingPayment().getReleaseEncumbrance())) {
      encumbrance.getEncumbrance().setStatus(Encumbrance.Status.RELEASED);
    }

    // set awaiting payment value
    MonetaryAmount awaitingPayment = Money.of(encumbrance.getEncumbrance().getAmountAwaitingPayment(), encumbrance.getCurrency()).add(ppAmountTotal);
    encumbrance.getEncumbrance().setAmountAwaitingPayment(awaitingPayment.with(getDefaultRounding()).getNumber().doubleValue());

    MonetaryAmount amount = Money.of(encumbrance.getAmount(), encumbrance.getCurrency()).subtract(ppAmountTotal);
    encumbrance.setAmount(amount.with(getDefaultRounding()).getNumber().doubleValue());
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

        MonetaryAmount encumbered = Money.of(budget.getEncumbered(), currency);

        budget.setEncumbered(encumbered.add(amount).getNumber().doubleValue());
        budgetService.updateBudgetMetadata(budget, transaction);
        budgetService.clearReadOnlyFields(budget);
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
       double newEncumbered = MonetaryFunctions.max().apply(encumbered.subtract(amount), Money.zero(currency)).getNumber().doubleValue();
       double newAwaitingPayment = awaitingPayment.add(amount).getNumber().doubleValue();
       budget.setEncumbered(newEncumbered);
       budget.setAwaitingPayment(newAwaitingPayment);
       budgetService.updateBudgetMetadata(budget, transaction);
       budgetService.clearReadOnlyFields(budget);
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
        transaction.setAmount(0.00);
        budgetService.updateBudgetMetadata(budget, transaction);
        budgetService.clearReadOnlyFields(budget);
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
