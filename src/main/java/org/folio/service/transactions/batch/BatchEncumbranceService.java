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
import static org.folio.service.transactions.batch.EncumbranceUtil.calculateNewAmount;
import static org.folio.service.transactions.batch.EncumbranceUtil.defaultNewAmountOnUnrelease;
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
    double oldInitialAmount = existingEncumbrance.getEncumbrance().getInitialAmountEncumbered();
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    double newEncumbered = budget.getEncumbered();
    if (isEncumbranceReleased(encumbrance)) {
      encumbrance.setAmount(0d);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    } else if (isTransitionFromUnreleasedToPending(encumbrance, existingEncumbrance)) {
      encumbrance.setAmount(0d);
      encumbrance.getEncumbrance().setInitialAmountEncumbered(0d);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    } else if (isTransitionFromPendingToUnreleased(encumbrance, existingEncumbrance)) {
      double awaitingPayment = existingEncumbrance.getEncumbrance().getAmountAwaitingPayment();
      double expended = existingEncumbrance.getEncumbrance().getAmountExpended();
      double credited = encumbrance.getEncumbrance().getAmountCredited();
      // prevent the new encumbrance from exceeding the initial amount or from going negative
      double newAmount = calculateNewAmount(encumbrance, currency, awaitingPayment, expended, credited);
      newAmount = defaultNewAmountOnUnrelease(encumbrance, newAmount, Encumbrance.Status.PENDING);
      log.info("updateBudget::pending->unreleased Encumbrance initialAmount={} oldAmount={} newAmount={}", initialAmount, encumbrance.getAmount(), newAmount);
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(currency, newEncumbered, newAmount);
    } else if (isTransitionFromReleasedToUnreleased(encumbrance, existingEncumbrance)) {
      double awaitingPayment = encumbrance.getEncumbrance().getAmountAwaitingPayment();
      double expended = encumbrance.getEncumbrance().getAmountExpended();
      double credited = encumbrance.getEncumbrance().getAmountCredited();
      // prevent the new encumbrance from exceeding the initial amount or from going below initial amount
      double newAmount;
      if (isNonZeroEncumbrance(encumbrance)) {
        newAmount = calculateNewAmount(encumbrance, currency, awaitingPayment, expended, credited);
        newAmount = defaultNewAmountOnUnrelease(encumbrance, newAmount, Encumbrance.Status.RELEASED);
      } else {
        newAmount = initialAmount;
      }
      log.info("updateBudget::released->unreleased Encumbrance initialAmount={} oldAmount={} newAmount={}", initialAmount, encumbrance.getAmount(), newAmount);
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(newEncumbered, newAmount, currency);
    } else {
      // prevent the new encumbrance from exceeding the initial amount but allow it to go negative
      double newAmount = isNonZeroEncumbrance(encumbrance) ? encumbrance.getAmount() : Math.min(encumbrance.getAmount(), oldInitialAmount);
      log.info("updateBudget::else Encumbrance initialAmount={} oldAmount={} newAmount={}", oldInitialAmount, encumbrance.getAmount(), newAmount);
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(newEncumbered, newAmount, currency);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    }
    budget.setEncumbered(newEncumbered);
  }

  private static boolean isNonZeroEncumbrance(Transaction encumbrance) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    return initialAmount > 0 && encumbrance.getAmount() <= initialAmount;
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
