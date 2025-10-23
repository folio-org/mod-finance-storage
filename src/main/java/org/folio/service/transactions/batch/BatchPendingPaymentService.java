package org.folio.service.transactions.batch;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.RELEASED;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.service.transactions.batch.EncumbranceUtil.updateNewAmountWithUnappliedCreditOnApproval;
import static org.folio.service.transactions.batch.EncumbranceUtil.calculateNewAmountDefaultOnZero;
import static org.folio.utils.CalculationUtils.calculateBudgetSummaryFields;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

import java.util.List;
import java.util.Map;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import lombok.extern.log4j.Log4j2;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;

@Log4j2
public class BatchPendingPaymentService extends AbstractBatchTransactionService {

  @Override
  public TransactionType getTransactionType() {
    return PENDING_PAYMENT;
  }

  @Override
  public void prepareCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder) {
    processPendingPayments(transactionsToCreate, holder, false);
  }

  @Override
  public void prepareUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder) {
    processPendingPayments(transactionsToUpdate, holder, false);
  }

  @Override
  public void prepareDeletingTransactions(List<Transaction> transactionsToDelete, BatchTransactionHolder holder) {
    processPendingPayments(transactionsToDelete, holder, true);
  }

  private void processPendingPayments(List<Transaction> pendingPayments, BatchTransactionHolder holder, boolean delete) {
    Map<String, Transaction> existingTransactionMap = holder.getExistingTransactionMap();
    List<Transaction> encumbrances = prepareEncumbrancesToProcess(pendingPayments, holder,
      tr -> tr.getAwaitingPayment() != null ? tr.getAwaitingPayment().getEncumbranceId() : null);
    if (!encumbrances.isEmpty()) {
      updateEncumbrances(encumbrances, existingTransactionMap, pendingPayments, delete);
    }
    applyPendingPayments(pendingPayments, holder, delete);
  }

  private void updateEncumbrances(List<Transaction> encumbrances, Map<String, Transaction> existingTransactionMap,
                                  List<Transaction> pendingPayments, boolean delete) {
    Map<String, List<Transaction>> pendingPaymentsByEncumbranceId = pendingPayments.stream()
      .filter(tr -> tr.getAwaitingPayment() != null && tr.getAwaitingPayment().getEncumbranceId() != null)
      .collect(groupingBy(tr -> tr.getAwaitingPayment().getEncumbranceId()));
    encumbrances.forEach(encumbrance -> updateEncumbrance(encumbrance,
      pendingPaymentsByEncumbranceId.get(encumbrance.getId()), existingTransactionMap, delete));
    encumbrances.stream()
      .filter(encumbrance -> encumbrance.getAmount() < 0)
      .forEach(encumbrance -> encumbrance.setAmount(0d));
  }

  private void updateEncumbrance(Transaction encumbrance, List<Transaction> pendingPayments,
                                 Map<String, Transaction> existingTransactionMap, boolean delete) {
    boolean releaseEncumbrance = false;
    for (Transaction pendingPayment : pendingPayments) {
      CurrencyUnit currency = Monetary.getCurrency(pendingPayment.getCurrency());
      Transaction existingTransaction = existingTransactionMap.get(pendingPayment.getId());
      double existingAmount = existingTransaction == null ? 0d : existingTransaction.getAmount();
      if (delete || cancelledTransaction(pendingPayment, existingTransactionMap)) {
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
    double awaitingPayment = encumbrance.getEncumbrance().getAmountAwaitingPayment();
    double newAwaitingPayment = subtractMoney(awaitingPayment, amount, currency);
    double expended = encumbrance.getEncumbrance().getAmountExpended();
    double credited = encumbrance.getEncumbrance().getAmountCredited();
    if (encumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED) {
      amount = calculateNewAmountDefaultOnZero(encumbrance, currency, newAwaitingPayment, expended, credited);
      encumbrance.setAmount(amount);
    }
    log.info("updateEncumbranceToCancelTransaction:: Awaiting payment oldAmount={} amount={} newAmount={}", awaitingPayment, amount, newAwaitingPayment);
    encumbrance.getEncumbrance().setAmountAwaitingPayment(newAwaitingPayment);
  }

  private void updateEncumbranceToApplyTransaction(Transaction encumbrance, double amount, CurrencyUnit currency) {
    double awaitingPayment = encumbrance.getEncumbrance().getAmountAwaitingPayment();
    double newAwaitingPayment = sumMoney(awaitingPayment, amount, currency);
    if (encumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED) {
      double newAmount = subtractMoney(encumbrance.getAmount(), amount, currency);
      // Add unapplied credit amount back to the encumbrance on invoice approval if the awaiting payment is negative
      updateNewAmountWithUnappliedCreditOnApproval(encumbrance, currency, awaitingPayment, newAmount);
    }
    log.info("updateEncumbranceToApplyTransaction:: Awaiting payment oldAmount={} amount={} newAmount={}", awaitingPayment, amount, newAwaitingPayment);
    encumbrance.getEncumbrance().setAmountAwaitingPayment(newAwaitingPayment);
  }

  private void applyPendingPayments(List<Transaction> pendingPayments, BatchTransactionHolder holder, boolean delete) {
    Map<String, Transaction> existingTransactionMap = holder.getExistingTransactionMap();
    Map<Budget, List<Transaction>> budgetToTransactions = createBudgetMapForTransactions(pendingPayments, holder.getBudgets());
    if (budgetToTransactions.isEmpty()) {
      return;
    }
    budgetToTransactions.forEach((budget, budgetPendingPayments) -> {
      for (Transaction pendingPayment : budgetPendingPayments) {
        CurrencyUnit currency = Monetary.getCurrency(pendingPayment.getCurrency());
        if (delete || cancelledTransaction(pendingPayment, existingTransactionMap)) {
          cancelPendingPayment(budget, pendingPayment, currency);
        } else {
          applyPendingPayment(budget, pendingPayment, existingTransactionMap.get(pendingPayment.getId()), currency);
        }
      }
      calculateBudgetSummaryFields(budget);
      updateBudgetMetadata(budget, budgetPendingPayments.getFirst());
    });
  }

  private void cancelPendingPayment(Budget budget, Transaction pendingPayment, CurrencyUnit currency) {
    budget.setAwaitingPayment(subtractMoney(budget.getAwaitingPayment(), pendingPayment.getAmount(), currency));
    pendingPayment.setVoidedAmount(pendingPayment.getAmount());
    pendingPayment.setAmount(0d);
  }

  private void applyPendingPayment(Budget budget, Transaction pendingPayment, Transaction existingTransaction, CurrencyUnit currency) {
    double amount = pendingPayment.getAmount();
    if (existingTransaction != null) {
      amount = subtractMoney(amount, existingTransaction.getAmount(), currency);
    }
    budget.setAwaitingPayment(sumMoney(budget.getAwaitingPayment(), amount, currency));
  }
}
