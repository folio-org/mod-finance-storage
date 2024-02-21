package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

public class EncumbranceService extends AbstractTransactionService implements TransactionManagingStrategy {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String SELECT_BUDGETS_BY_ORDER_ID_FOR_UPDATE =
    "SELECT b.jsonb FROM %s b INNER JOIN (SELECT DISTINCT budgets.id FROM %s budgets INNER JOIN %s transactions "
    + "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId "
    + "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = $1) sub ON sub.id = b.id "
    + "FOR UPDATE OF b";
  public static final String FOR_UPDATE = "FOR_UPDATE";
  public static final String FOR_CREATE = "FOR_CREATE";
  public static final String EXISTING = "EXISTING";

  private final AllOrNothingTransactionService allOrNothingEncumbranceService;
  private final BudgetService budgetService;

  public EncumbranceService(AllOrNothingTransactionService allOrNothingEncumbranceService,
                            TransactionDAO transactionDAO,
                            BudgetService budgetService) {
    super(transactionDAO);
    this.allOrNothingEncumbranceService = allOrNothingEncumbranceService;
    this.budgetService = budgetService;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, DBConn conn) {
    return allOrNothingEncumbranceService.createTransaction(transaction, conn, this::processEncumbrances);
  }

  @Override
  public Future<Void> updateTransaction(Transaction transaction, DBConn conn) {
    return allOrNothingEncumbranceService.updateTransaction(transaction, conn, this::processEncumbrances);
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    Map<String, List<Transaction>> groupedTransactions = existingTransactions.stream().collect(groupingBy(Transaction::getFromFundId));
    return budgets.stream().collect(toMap(identity(), budget -> groupedTransactions.getOrDefault(budget.getFundId(), Collections.emptyList())));
  }

  private String getSelectBudgetsQueryForUpdate(String tenantId) {
    String budgetTableName = getFullTableName(tenantId, BUDGET_TABLE);
    String transactionTableName = getFullTableName(tenantId, TEMPORARY_ORDER_TRANSACTIONS);
    return String.format(SELECT_BUDGETS_BY_ORDER_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);
  }

  public Transaction.TransactionType getStrategyName() {
    return Transaction.TransactionType.ENCUMBRANCE;
  }

  /**
   * To prevent partial encumbrance transactions for an order, all the encumbrances must be created following All or nothing
   */
  public Future<Void> processEncumbrances(List<Transaction> tmpTransactions, DBConn conn) {
    return getTransactionsForCreateAndUpdate(tmpTransactions, conn)
      .compose(transactionsForCreateAndUpdate -> updateBudgetsTotals(transactionsForCreateAndUpdate, tmpTransactions, conn)
        .map(v -> findTransactionForUpdate(transactionsForCreateAndUpdate.get(FOR_UPDATE),
                                                                transactionsForCreateAndUpdate.get(EXISTING)))
        .compose(transactions -> transactionDAO.updatePermanentTransactions(transactions, conn))
        .compose(ok -> {
          if (!transactionsForCreateAndUpdate.get(FOR_CREATE).isEmpty()) {
            List<String> ids = transactionsForCreateAndUpdate.get(FOR_CREATE)
              .stream()
              .map(Transaction::getId)
              .collect(toList());
            return transactionDAO.saveTransactionsToPermanentTable(ids, conn);
          } else {
            return Future.succeededFuture();
          }
        }))
      .mapEmpty();
  }

  private List<Budget> updateBudgetsTotalsForCreatingTransactions(List<Transaction> tempTransactions, List<Budget> budgets) {
    // create tr
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
          budgetService.updateBudgetMetadata(budget, tmpTransaction);
          budgetService.clearReadOnlyFields(budget);
        });
    }
    return budget;
  }

  private Future<Map<String, List<Transaction>>> getTransactionsForCreateAndUpdate(List<Transaction> tmpTransactions, DBConn conn) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");

    tmpTransactions.stream()
      .map(Transaction::getId)
      .forEach(id -> criterionBuilder.with("id", id));

    return transactionDAO.getTransactions(criterionBuilder.build(), conn)
      .map(trs -> groupTransactionForCreateAndUpdate(tmpTransactions, trs));
  }

  private Map<String, List<Transaction>> groupTransactionForCreateAndUpdate(List<Transaction> tmpTransactions, List<Transaction> permanentTransactions) {
    Map<String, List<Transaction>> groupedTransactions = new HashMap<>();
    Set<String> ids = permanentTransactions.stream()
      .map(Transaction::getId)
      .collect(Collectors.toSet());

    groupedTransactions.put(FOR_UPDATE, tmpTransactions.stream().filter(tr -> ids.contains(tr.getId())).collect(Collectors.toList()));
    groupedTransactions.put(FOR_CREATE, tmpTransactions.stream().filter(tr -> !ids.contains(tr.getId())).collect(Collectors.toList()));
    groupedTransactions.put(EXISTING, permanentTransactions);
    return groupedTransactions;
  }

  private List<Transaction> findTransactionForUpdate(List<Transaction> incomingTransactions, List<Transaction> existingTransactions) {
    // if nothing to update
    if (existingTransactions.isEmpty()) {
      return new ArrayList<>();
    }
    Map<String, Transaction> groupedExistingTransactions = existingTransactions.stream().collect(toMap(Transaction::getId, identity()));

    return incomingTransactions.stream()
      // filter transactions for update
      .filter(incomingTransaction -> groupedExistingTransactions.containsKey(incomingTransaction.getId()))
      .filter(incomingTransaction -> {
       Transaction existingTransaction = groupedExistingTransactions.get(incomingTransaction.getId());
        return isNotFromReleasedExceptToUnreleased(incomingTransaction, existingTransaction)
                    || isEncumbranceOrderStatusUpdated(incomingTransaction,  existingTransaction);
      })
      .collect(Collectors.toList());
  }

  private boolean isEncumbranceOrderStatusUpdated(Transaction incomingTransaction, Transaction existingTransaction) {
    return Encumbrance.OrderStatus.CLOSED == incomingTransaction.getEncumbrance().getOrderStatus()
                && Encumbrance.OrderStatus.OPEN == existingTransaction.getEncumbrance().getOrderStatus();
  }

  private Future<Void> updateBudgetsTotals(Map<String, List<Transaction>> groupedTransactions,
      List<Transaction> newTransactions, DBConn conn) {

    return budgetService.getBudgets(getSelectBudgetsQueryForUpdate(conn.getTenantId()),
        Tuple.of(newTransactions.get(0).getEncumbrance().getSourcePurchaseOrderId()), conn)
      .compose(oldBudgets -> {

        List<Budget> updatedBudgets = updateBudgetsTotalsForCreatingTransactions(groupedTransactions.get(FOR_CREATE), oldBudgets);
        List<Budget> finalNewBudgets = updateBudgetsTotalsForUpdatingTransactions(groupedTransactions.get(EXISTING),
            groupedTransactions.get(FOR_UPDATE), updatedBudgets);

        return budgetService.updateBatchBudgets(finalNewBudgets, conn);
      });
  }


  private List<Budget> updateBudgetsTotalsForUpdatingTransactions(List<Transaction> existingTransactions, List<Transaction> tempTransactions, List<Budget> budgets) {
    // if nothing to update
    if (existingTransactions.isEmpty()) {
      return budgets;
    }
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
          if (isNotFromReleasedExceptToUnreleased(tmpTransaction, existingTransaction)) {
            processBudget(budget, currency, tmpTransaction, existingTransaction);
          }
        });
    }
    return budget;
  }

  private void processBudget(Budget budget, CurrencyUnit currency, Transaction tmpTransaction, Transaction existingTransaction) {

    double newEncumbered = budget.getEncumbered();
    if (isEncumbranceReleased(tmpTransaction)) {
      newEncumbered = subtractMoney(newEncumbered, tmpTransaction.getAmount(), currency);
      tmpTransaction.setAmount(0d);
    } else  if (isTransitionFromUnreleasedToPending(tmpTransaction, existingTransaction)) {
      tmpTransaction.setAmount(0d);
      tmpTransaction.getEncumbrance().setInitialAmountEncumbered(0d);
      newEncumbered = subtractMoney(newEncumbered, existingTransaction.getAmount(), currency);
    } else if (isTransitionFromPendingToUnreleased(tmpTransaction, existingTransaction)) {
      double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(), existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
      newAmount = subtractMoney(newAmount, existingTransaction.getEncumbrance().getAmountExpended(), currency);
      tmpTransaction.setAmount(newAmount);
      newEncumbered = sumMoney(currency, newEncumbered, newAmount);
    } else if (isTransitionFromReleasedToUnreleased(tmpTransaction, existingTransaction)) {
      double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(),
        tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
      newAmount = subtractMoney(newAmount, tmpTransaction.getEncumbrance().getAmountExpended(), currency);
      tmpTransaction.setAmount(newAmount);
      newEncumbered = sumMoney(newEncumbered, newAmount, currency);
    } else {
      newEncumbered = sumMoney(newEncumbered, tmpTransaction.getAmount(), currency);
      newEncumbered = subtractMoney(newEncumbered, existingTransaction.getAmount(), currency);
    }

    budget.setEncumbered(newEncumbered);
    budgetService.updateBudgetMetadata(budget, tmpTransaction);
    budgetService.clearReadOnlyFields(budget);
  }


  private boolean isEncumbranceReleased(Transaction transaction) {
    return transaction.getEncumbrance()
      .getStatus() == Encumbrance.Status.RELEASED;
  }

  private boolean isNotFromReleasedExceptToUnreleased(Transaction newTransaction, Transaction existingTransaction) {
    return existingTransaction.getEncumbrance().getStatus() != Encumbrance.Status.RELEASED ||
      newTransaction.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED;
  }

  private boolean isTransitionFromUnreleasedToPending(Transaction newTransaction, Transaction existingTransaction) {
    return existingTransaction.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED
      && newTransaction.getEncumbrance().getStatus() == Encumbrance.Status.PENDING;
  }

  private boolean isTransitionFromPendingToUnreleased(Transaction newTransaction, Transaction existingTransaction) {
    return existingTransaction.getEncumbrance().getStatus() == Encumbrance.Status.PENDING
      && newTransaction.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED;
  }

  private boolean isTransitionFromReleasedToUnreleased(Transaction newTransaction, Transaction existingTransaction) {
    return existingTransaction.getEncumbrance().getStatus() == Encumbrance.Status.RELEASED
      && newTransaction.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED;
  }
}
