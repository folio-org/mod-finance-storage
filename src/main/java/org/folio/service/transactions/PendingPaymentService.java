package org.folio.service.transactions;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.TRUE;
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
import static org.folio.rest.util.ErrorCodes.OUTDATED_FUND_ID_IN_ENCUMBRANCE;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exception.HttpException;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.transactions.cancel.CancelTransactionService;
import org.javamoney.moneta.Money;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.javamoney.moneta.function.MonetaryFunctions;

public class PendingPaymentService implements TransactionManagingStrategy {

  private static final Logger logger = LogManager.getLogger(PendingPaymentService.class);

  public static final String SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE =
    "SELECT b.jsonb FROM %s b INNER JOIN (SELECT DISTINCT budgets.id FROM %s budgets INNER JOIN %s transactions "
    + "ON (budgets.fundId = transactions.fromFundId  AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = $1 AND transactions.jsonb ->> 'transactionType' = 'Pending payment') "
    + "sub ON sub.id = b.id "
    + "FOR UPDATE OF b";

  private final AllOrNothingTransactionService allOrNothingTransactionService;
  private final TransactionDAO transactionsDAO;
  private final BudgetService budgetService;
  private final CancelTransactionService cancelTransactionService;

  public PendingPaymentService(AllOrNothingTransactionService allOrNothingTransactionService,
                               TransactionDAO transactionsDAO,
                               BudgetService budgetService,
                               CancelTransactionService cancelTransactionService) {
    this.allOrNothingTransactionService = allOrNothingTransactionService;
    this.transactionsDAO = transactionsDAO;
    this.budgetService = budgetService;
    this.cancelTransactionService = cancelTransactionService;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, RequestContext requestContext) {
    return allOrNothingTransactionService.createTransaction(transaction, requestContext, this::createTransactions);
  }

  @Override
  public Future<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    return allOrNothingTransactionService.updateTransaction(transaction, requestContext, this::cancelAndUpdateTransactions);
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

  public Future<Void> cancelAndUpdateTransactions(List<Transaction> tmpTransactions, DBClient client) {
    return getTransactions(tmpTransactions, client)
      .compose(existingTransactions -> {
        List<String> idsToCancel = tmpTransactions.stream()
          .filter(tr -> TRUE.equals(tr.getInvoiceCancelled()))
          .map(Transaction::getId)
          .filter(id -> existingTransactions.stream().anyMatch(
            tr -> tr.getId().equals(id) && !TRUE.equals(tr.getInvoiceCancelled())))
          .collect(toList());
        List<Transaction> tmpTransactionsToCancel = tmpTransactions.stream()
          .filter(tr -> idsToCancel.contains(tr.getId()))
          .collect(toList());
        List<Transaction> tmpTransactionsToUpdate = tmpTransactions.stream()
          .filter(tr -> !idsToCancel.contains(tr.getId()))
          .collect(toList());
        List<Transaction> existingTransactionsToUpdate = existingTransactions.stream()
          .filter(tr -> !idsToCancel.contains(tr.getId()))
          .collect(toList());
        return cancelTransactions(tmpTransactionsToCancel, client)
          .compose(v -> updateTransactions(tmpTransactionsToUpdate, existingTransactionsToUpdate, client));
    });
  }

  private Future<Void> cancelTransactions(List<Transaction> tmpTransactions, DBClient client) {
    if (tmpTransactions.size() == 0)
      return succeededFuture();
    return cancelTransactionService.cancelTransactions(tmpTransactions, client)
      .map(voidedTransactions -> null);
  }

  private Future<Void> updateTransactions(List<Transaction> tmpTransactions, List<Transaction> existingTransactions, DBClient client) {
    if (tmpTransactions.size() == 0)
      return succeededFuture();
    List<Transaction> transactions = createDifferenceTransactions(tmpTransactions, existingTransactions);
    return processPendingPayments(transactions, client)
      .map(processedTransactions -> null)
      .compose(v -> transactionsDAO.updatePermanentTransactions(tmpTransactions, client));
  }

  private Future<List<Transaction>> processPendingPayments(List<Transaction> transactions, DBClient client) {
    List<Transaction> linkedToEncumbrance = transactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getAwaitingPayment()) && Objects.nonNull(transaction.getAwaitingPayment().getEncumbranceId()))
      .collect(Collectors.toList());
    List<Transaction> notLinkedToEncumbrance = transactions.stream()
      .filter(transaction -> Objects.isNull(transaction.getAwaitingPayment()) || Objects.isNull(transaction.getAwaitingPayment().getEncumbranceId()))
      .collect(Collectors.toList());

    String summaryId = getSummaryId(transactions.get(0));

    return budgetService.getBudgets(getSelectBudgetsQueryForUpdate(client.getTenantId()), Tuple.of(UUID.fromString(summaryId)), client)
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
    return succeededFuture(oldBudgets);
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
        if (budget == null) {
          List<Parameter> parameters = new ArrayList<>();
          parameters.add(new Parameter().withKey("encumbranceId").withValue(transaction.getId()));
          parameters.add(new Parameter().withKey("fundId").withValue(transaction.getFromFundId()));
          parameters.add(new Parameter().withKey("poLineId").withValue(transaction.getEncumbrance().getSourcePoLineId()));
          Error error = OUTDATED_FUND_ID_IN_ENCUMBRANCE.toError().withParameters(parameters);
          logger.error("applyNegativeEncumbrances:: Applying negative encumbrances failed {}", JsonObject.mapFrom(error).encodePrettily());
          throw new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), error);
        }
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

    // sort pending payments by amount to apply negative amounts first
    List<Transaction> sortedPendingPayments = pendingPayments.stream()
      .sorted(Comparator.comparing(Transaction::getAmount))
      .collect(toList());
    sortedPendingPayments.forEach(transaction -> {
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

  private String getSelectBudgetsQueryForUpdate(String tenantId) {
    String budgetTableName = getFullTableName(tenantId, BUDGET_TABLE);
    String transactionTableName = getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS);
    return String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);
  }

}
