package org.folio.service.transactions;

import static java.lang.Math.max;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.money.Monetary.getDefaultRounding;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
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

public class PendingPaymentAllOrNothingService extends BaseAllOrNothingTransactionService<InvoiceTransactionSummary> {

  public static final String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON (budgets.fundId = transactions.fromFundId  AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = ? AND transactions.jsonb ->> 'transactionType' = 'Pending payment'";

  public PendingPaymentAllOrNothingService(BudgetService budgetService,
                                           TemporaryTransactionDAO temporaryTransactionDAO,
                                           LedgerFiscalYearService ledgerFiscalYearService,
                                           FundService fundService,
                                           TransactionSummaryService<InvoiceTransactionSummary> transactionSummaryService,
                                           TransactionDAO transactionsDAO,
                                           LedgerService ledgerService) {
    super(budgetService, temporaryTransactionDAO, ledgerFiscalYearService, fundService, transactionSummaryService, transactionsDAO, ledgerService);
  }


  @Override
  Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client) {
    List<Transaction> linkedToEncumbrance = transactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getAwaitingPayment()) && Objects.nonNull(transaction.getAwaitingPayment().getEncumbranceId()))
      .collect(Collectors.toList());
    List<Transaction> notLinkedToEncumbrance = transactions.stream()
      .filter(transaction -> Objects.isNull(transaction.getAwaitingPayment()) || Objects.isNull(transaction.getAwaitingPayment().getEncumbranceId()))
      .collect(Collectors.toList());

    String summaryId = getSummaryId(transactions.get(0));

    JsonArray params = new JsonArray();
    params.add(summaryId);


    return budgetService.getBudgets(getSelectBudgetsQuery(client.getTenantId()), params, client)
      .compose(oldBudgets -> processLinkedPendingPayments(linkedToEncumbrance, oldBudgets, client)
      .map(budgets -> processNotLinkedPendingPayments(notLinkedToEncumbrance, budgets))
      .compose(newBudgets -> budgetService.updateBatchBudgets(newBudgets, client)
        .compose(integer -> updateLedgerFYsWithTotals(oldBudgets, newBudgets, client))))
      .compose(aVoid -> transactionsDAO.saveTransactionsToPermanentTable(transactions.get(0).getSourceInvoiceId(), client))
      .mapEmpty();
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
    MultiKeyMap<String, Budget> groupedBudgets = new MultiKeyMap<>();
    groupedBudgets.putAll(budgets.stream().collect(toMap(budget -> new MultiKey<>(budget.getFundId(), budget.getFiscalYearId()), identity())));

    return existingTransactions.stream()
      .collect(groupingBy(
        transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

  }

  private Budget updateBudgetTotalsWithNotLinkedPendingPayments(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getAmount(), currency);
          budget.setAwaitingPayment(newAwaitingPayment);
          recalculateAvailableUnavailable(budget, currency);
        });
    }
    return budget;
  }

  private void recalculateAvailableUnavailable(Budget budget, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoneyNonNegative(budget.getAllocated(), newUnavailable, currency);

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
    MonetaryAmount amount = Money.of(encumbrance.getAmount(), encumbrance.getCurrency()).subtract(transactions.stream()
      .map(transaction -> Money.of(transaction.getAmount(), transaction.getCurrency()))
      .reduce(Money::add).orElse(Money.zero(Monetary.getCurrency(encumbrance.getCurrency()))));
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

        MonetaryAmount available = Money.of(budget.getAvailable(), currency);
        MonetaryAmount unavailable = Money.of(budget.getUnavailable(), currency);

        double newAvailable = max(available.subtract(amount).getNumber().doubleValue(), 0);
        double newUnavailable = unavailable.add(amount).getNumber().doubleValue();

        budget.setAvailable(newAvailable);
        budget.setUnavailable(newUnavailable);

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
       double newEncumbered = max(encumbered.subtract(amount).getNumber().doubleValue(), 0);
       double newAwaitingPayment = max(awaitingPayment.add(amount).getNumber().doubleValue(), 0);
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
        MonetaryAmount available = Money.of(budget.getAvailable(), currency);
        MonetaryAmount unavailable = Money.of(budget.getUnavailable(), currency);
        double newEncumbered = max(encumbered.subtract(amount).getNumber().doubleValue(), 0);
        double newAvailable = max(available.add(amount).getNumber().doubleValue(), 0);
        double newUnavailable = max(unavailable.subtract(amount).getNumber().doubleValue(), 0);
        budget.setEncumbered(newEncumbered);
        budget.setAvailable(newAvailable);
        budget.setUnavailable(newUnavailable);
        transaction.setAmount(0.00);
      });
    }

  }

  @Override
  String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  @Override
  Void handleValidationError(Transaction transaction) {

    List<Error> errors = new ArrayList<>(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }

  @Override
  protected String getSelectBudgetsQuery(String tenantId) {
    return getSelectBudgetsQuery(SELECT_BUDGETS_BY_INVOICE_ID, tenantId, TEMPORARY_INVOICE_TRANSACTIONS);
  }

  @Override
  protected boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget) {
    return ledger.getRestrictExpenditures() && budget.getAllowableExpenditure() != null;
  }
}
