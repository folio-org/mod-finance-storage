package org.folio.service.transactions.batch;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.RELEASED;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.utils.CalculationUtils.calculateBudgetSummaryFields;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;

public class BatchPendingPaymentService extends AbstractBatchTransactionService {

  @Override
  public TransactionType getTransactionType() {
    return PENDING_PAYMENT;
  }

  @Override
  public void updatesForCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder) {
    processPendingPayments(transactionsToCreate, holder);
  }

  @Override
  public void updatesForUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder) {
    processPendingPayments(transactionsToUpdate, holder);
  }

  private void processPendingPayments(List<Transaction> pendingPayments, BatchTransactionHolder holder) {
    Map<String, Transaction> existingTransactionMap = holder.getExistingTransactionMap();
    List<Transaction> encumbrances = prepareEncumbrancesToProcess(pendingPayments, holder,
      tr -> tr.getAwaitingPayment() != null ? tr.getAwaitingPayment().getEncumbranceId() : null);
    if (!encumbrances.isEmpty()) {
      updateEncumbrancesTotals(encumbrances, existingTransactionMap, pendingPayments);
    }
    updateBudgetsTotalsWithPendingPayments(pendingPayments, encumbrances, holder);
  }

  private void updateEncumbrancesTotals(List<Transaction> encumbrances,
      Map<String, Transaction> existingTransactionMap, List<Transaction> pendingPayments) {
    Map<String, List<Transaction>> pendingPaymentsByEncumbranceId = pendingPayments.stream()
      .filter(tr -> tr.getAwaitingPayment() != null && tr.getAwaitingPayment().getEncumbranceId() != null)
      .collect(groupingBy(tr -> tr.getAwaitingPayment().getEncumbranceId()));
    encumbrances.forEach(encumbrance -> updateEncumbranceTotals(encumbrance,
      pendingPaymentsByEncumbranceId.get(encumbrance.getId()), existingTransactionMap));
  }

  private void updateEncumbranceTotals(Transaction encumbrance, List<Transaction> pendingPayments,
      Map<String, Transaction> existingTransactionMap) {
    boolean releaseEncumbrance = false;
    for (Transaction pendingPayment : pendingPayments) {
      CurrencyUnit currency = Monetary.getCurrency(pendingPayment.getCurrency());
      Transaction existingTransaction = existingTransactionMap.get(pendingPayment.getId());
      double existingAmount = existingTransaction == null ? 0d : existingTransaction.getAmount();
      if (cancelledTransaction(pendingPayment, existingTransactionMap)) {
        updateEncumbranceToCancelTransaction(encumbrance, existingAmount, currency);
      } else {
        double amount = subtractMoney(pendingPayment.getAmount(), existingAmount, currency);
        updateEncumbranceToApplyTransaction(encumbrance, amount, currency);
        if (!releaseEncumbrance && TRUE.equals(pendingPayment.getAwaitingPayment().getReleaseEncumbrance())) {
          releaseEncumbrance = true;
        }
      }
    }
    if (releaseEncumbrance) {
      encumbrance.getEncumbrance().setStatus(RELEASED);
    }
  }

  private void updateEncumbranceToCancelTransaction(Transaction encumbrance, double amount, CurrencyUnit currency) {
    if (RELEASED != encumbrance.getEncumbrance().getStatus()) {
      encumbrance.setAmount(sumMoney(encumbrance.getAmount(), amount, currency));
    }
    encumbrance.getEncumbrance().setAmountAwaitingPayment(subtractMoney(
      encumbrance.getEncumbrance().getAmountAwaitingPayment(), amount, currency));
  }

  private void updateEncumbranceToApplyTransaction(Transaction encumbrance, double amount, CurrencyUnit currency) {
    encumbrance.getEncumbrance().setAmountAwaitingPayment(sumMoney(
      encumbrance.getEncumbrance().getAmountAwaitingPayment(), amount, currency));
    encumbrance.setAmount(subtractMoney(encumbrance.getAmount(), amount, currency));
  }

  private void updateBudgetsTotalsWithPendingPayments(List<Transaction> pendingPayments,
      List<Transaction> encumbrances, BatchTransactionHolder holder) {
    encumbrances.stream()
      .filter(tr -> tr.getAmount() < 0)
      .forEach(tr -> tr.setAmount(0d));
    applyPendingPayments(pendingPayments, holder);
  }

  private void applyPendingPayments(List<Transaction> pendingPayments, BatchTransactionHolder holder) {
    Map<String, Transaction> existingTransactionMap = holder.getExistingTransactionMap();
    List<Budget> budgets = holder.getBudgets();
    Map<Budget, List<Transaction>> budgetToTransactions = createBudgetMapForTransactions(pendingPayments, budgets);
    if (budgetToTransactions.isEmpty()) {
      return;
    }
    budgetToTransactions.forEach((budget, transactions) -> {
      // sort pending payments by amount to apply negative amounts first
      List<Transaction> sortedPendingPayments = transactions.stream()
        .sorted(Comparator.comparing(Transaction::getAmount))
        .toList();
      for (Transaction pendingPayment : sortedPendingPayments) {
        CurrencyUnit currency = Monetary.getCurrency(pendingPayment.getCurrency());
        if (cancelledTransaction(pendingPayment, existingTransactionMap)) {
          cancelPendingPayment(budget, pendingPayment, currency);
        } else {
          applyPendingPayment(budget, pendingPayment, existingTransactionMap.get(pendingPayment.getId()), currency);
        }
      }
      calculateBudgetSummaryFields(budget);
      updateBudgetMetadata(budget, transactions.get(0));
    });
  }

  private void cancelPendingPayment(Budget budget, Transaction pendingPayment, CurrencyUnit currency) {
    budget.setAwaitingPayment(subtractMoney(budget.getAwaitingPayment(), pendingPayment.getAmount(), currency));
    pendingPayment.setVoidedAmount(pendingPayment.getAmount());
    pendingPayment.setAmount(0d);
  }

  private void applyPendingPayment(Budget budget, Transaction pendingPayment, Transaction existingTransaction,
      CurrencyUnit currency) {
    double amount = pendingPayment.getAmount();
    if (existingTransaction != null) {
      amount = subtractMoney(amount, existingTransaction.getAmount(), currency);
    }
    budget.setAwaitingPayment(sumMoney(budget.getAwaitingPayment(), amount, currency));
  }

}
