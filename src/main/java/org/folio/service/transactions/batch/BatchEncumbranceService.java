package org.folio.service.transactions.batch;

import lombok.extern.log4j.Log4j2;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.List;
import java.util.Map;

import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.utils.CalculationUtils.calculateBudgetSummaryFields;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

@Log4j2
public class BatchEncumbranceService extends AbstractBatchTransactionService {

  @Override
  public TransactionType getTransactionType() {
    return ENCUMBRANCE;
  }

  @Override
  public void prepareCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder) {
    createBudgetMapForTransactions(transactionsToCreate, holder.getBudgets())
      .forEach(this::updateBudgetForEncumbranceCreation);
  }

  @Override
  public void prepareUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder) {
    createBudgetMapForTransactions(transactionsToUpdate, holder.getBudgets())
      .forEach((budget, encumbrances) -> updateBudgetForEncumbranceUpdate(budget, encumbrances,
        holder.getExistingTransactionMap()));
  }

  private void updateBudgetForEncumbranceCreation(Budget budget, List<Transaction> encumbrances) {
    CurrencyUnit currency = Monetary.getCurrency(encumbrances.getFirst().getCurrency());
    encumbrances.forEach(encumbrance -> {
      double newEncumbered = sumMoney(budget.getEncumbered(), encumbrance.getAmount(), currency);
      budget.setEncumbered(newEncumbered);
    });
    calculateBudgetSummaryFields(budget);
    updateBudgetMetadata(budget, encumbrances.getFirst());
  }

  private void updateBudgetForEncumbranceUpdate(Budget budget, List<Transaction> encumbrances,
                                                Map<String, Transaction> existingTransactions) {
    CurrencyUnit currency = Monetary.getCurrency(encumbrances.getFirst().getCurrency());
    encumbrances.forEach(encumbrance -> {
      Transaction existingEncumbrance = existingTransactions.get(encumbrance.getId());
      if (isNotFromReleasedExceptToUnreleased(encumbrance, existingEncumbrance)) {
        updateBudget(budget, currency, encumbrance, existingEncumbrance);
      }
    });
    calculateBudgetSummaryFields(budget);
    updateBudgetMetadata(budget, encumbrances.getFirst());
  }

  private void updateBudget(Budget budget, CurrencyUnit currency, Transaction encumbrance, Transaction existingEncumbrance) {
    double newEncumbered = budget.getEncumbered();
    if (isEncumbranceReleased(encumbrance)) {
      encumbrance.setAmount(0d);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    } else if (isTransitionFromUnreleasedToPending(encumbrance, existingEncumbrance)) {
      encumbrance.setAmount(0d);
      encumbrance.getEncumbrance().setInitialAmountEncumbered(0d);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    } else if (isTransitionFromPendingToUnreleased(encumbrance, existingEncumbrance)) {
      double initialAmountEncumbered = encumbrance.getEncumbrance().getInitialAmountEncumbered();
      double awaitingPayment = existingEncumbrance.getEncumbrance().getAmountAwaitingPayment();
      double expended = existingEncumbrance.getEncumbrance().getAmountExpended();
      double credited = encumbrance.getEncumbrance().getAmountCredited();
      double newAmount = subtractMoney(initialAmountEncumbered, awaitingPayment, currency);
      newAmount = subtractMoney(newAmount, expended, currency);
      newAmount = sumMoney(newAmount, credited, currency);
      // prevent the new encumbrance from exceeding the initial encumbered amount or from going negative
      newAmount = capNewAmountOnUnrelease(Encumbrance.Status.PENDING, encumbrance, newAmount, 0d);
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(currency, newEncumbered, newAmount);
    } else if (isTransitionFromReleasedToUnreleased(encumbrance, existingEncumbrance)) {
      double initialAmountEncumbered = encumbrance.getEncumbrance().getInitialAmountEncumbered();
      double awaitingPayment = encumbrance.getEncumbrance().getAmountAwaitingPayment();
      double expended = encumbrance.getEncumbrance().getAmountExpended();
      double credited = encumbrance.getEncumbrance().getAmountCredited();
      // prevent the new encumbrance from exceeding the initial encumbered amount or from going below initial encumbered amount
      double newAmount;
      if (isNonZeroEncumbrance(encumbrance)) {
        newAmount = subtractMoney(initialAmountEncumbered, awaitingPayment, currency);
        newAmount = subtractMoney(newAmount, expended, currency);
        newAmount = sumMoney(newAmount, credited, currency);
        newAmount = capNewAmountOnUnrelease(Encumbrance.Status.RELEASED, encumbrance, newAmount, encumbrance.getEncumbrance().getInitialAmountEncumbered());
      } else {
        newAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
      }
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(newEncumbered, newAmount, currency);
    } else {
      // prevent the new encumbrance from exceeding the initial encumbered amount but allow it to go negative
      double newAmount;
      if (isNonZeroEncumbrance(encumbrance)) {
        newAmount = encumbrance.getAmount();
      } else {
        newAmount = Math.min(encumbrance.getAmount(), existingEncumbrance.getEncumbrance().getInitialAmountEncumbered());
      }
      log.info("updateBudget::else Encumbrance initialAmount={} oldAmount={} newAmount={}",
        encumbrance.getEncumbrance().getInitialAmountEncumbered(), encumbrance.getAmount(), newAmount);
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(newEncumbered, newAmount, currency);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    }
    budget.setEncumbered(newEncumbered);
  }

  private static boolean isNonZeroEncumbrance(Transaction encumbrance) {
    return encumbrance.getEncumbrance().getInitialAmountEncumbered() > 0
      && encumbrance.getAmount() <= encumbrance.getEncumbrance().getInitialAmountEncumbered();
  }

  private static double capNewAmountOnUnrelease(Encumbrance.Status fromStatus, Transaction encumbrance, double newAmount, double cappedAmount) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    if (newAmount > initialAmount) {
      log.warn("capNewAmountOnUnrelease:: Transition from {} to UNRELEASED with newAmount={} " +
          "exceeding initialAmount and will be capped at initialAmount={}", fromStatus.name(), newAmount, initialAmount);
      newAmount = initialAmount;
    } else if (newAmount < 0) {
      log.warn("capNewAmountOnUnrelease:: Transition from {} to UNRELEASED with newAmount={} " +
          "going below 0 and will be capped at cappedAmount={}",fromStatus.name(), newAmount, cappedAmount);
      newAmount = cappedAmount;
    }
    return newAmount;
  }

  private boolean isEncumbranceReleased(Transaction encumbrance) {
    return encumbrance.getEncumbrance().getStatus() == Encumbrance.Status.RELEASED;
  }

  private boolean isNotFromReleasedExceptToUnreleased(Transaction newEncumbrance, Transaction existingEncumbrance) {
    return existingEncumbrance.getEncumbrance().getStatus() != Encumbrance.Status.RELEASED ||
      newEncumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED;
  }

  private boolean isTransitionFromUnreleasedToPending(Transaction newEncumbrance, Transaction existingEncumbrance) {
    return existingEncumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED
      && newEncumbrance.getEncumbrance().getStatus() == Encumbrance.Status.PENDING;
  }

  private boolean isTransitionFromPendingToUnreleased(Transaction newEncumbrance, Transaction existingEncumbrance) {
    return existingEncumbrance.getEncumbrance().getStatus() == Encumbrance.Status.PENDING
      && newEncumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED;
  }

  private boolean isTransitionFromReleasedToUnreleased(Transaction newEncumbrance, Transaction existingEncumbrance) {
    return existingEncumbrance.getEncumbrance().getStatus() == Encumbrance.Status.RELEASED
      && newEncumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED;
  }
}
