package org.folio.service.transactions.batch;

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
    CurrencyUnit currency = Monetary.getCurrency(encumbrances.get(0).getCurrency());
    encumbrances.forEach(encumbrance -> {
      double newEncumbered = sumMoney(budget.getEncumbered(), encumbrance.getAmount(), currency);
      budget.setEncumbered(newEncumbered);
    });
    calculateBudgetSummaryFields(budget);
    updateBudgetMetadata(budget, encumbrances.get(0));
  }

  private void updateBudgetForEncumbranceUpdate(Budget budget, List<Transaction> encumbrances,
      Map<String, Transaction> existingTransactions) {
    CurrencyUnit currency = Monetary.getCurrency(encumbrances.get(0).getCurrency());
    encumbrances.forEach(encumbrance -> {
      Transaction existingEncumbrance = existingTransactions.get(encumbrance.getId());
      if (isNotFromReleasedExceptToUnreleased(encumbrance, existingEncumbrance)) {
        updateBudget(budget, currency, encumbrance, existingEncumbrance);
      }
    });
    calculateBudgetSummaryFields(budget);
    updateBudgetMetadata(budget, encumbrances.get(0));
  }

  private void updateBudget(Budget budget, CurrencyUnit currency, Transaction encumbrance, Transaction existingEncumbrance) {
    double newEncumbered = budget.getEncumbered();
    if (isEncumbranceReleased(encumbrance)) {
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
      encumbrance.setAmount(0d);
    } else  if (isTransitionFromUnreleasedToPending(encumbrance, existingEncumbrance)) {
      encumbrance.setAmount(0d);
      encumbrance.getEncumbrance().setInitialAmountEncumbered(0d);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    } else if (isTransitionFromPendingToUnreleased(encumbrance, existingEncumbrance)) {
      double newAmount = subtractMoney(encumbrance.getEncumbrance().getInitialAmountEncumbered(),
        existingEncumbrance.getEncumbrance().getAmountAwaitingPayment(), currency);
      newAmount = subtractMoney(newAmount, existingEncumbrance.getEncumbrance().getAmountExpended(), currency);
      newAmount = sumMoney(newAmount, encumbrance.getEncumbrance().getAmountCredited(), currency);
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(currency, newEncumbered, newAmount);
    } else if (isTransitionFromReleasedToUnreleased(encumbrance, existingEncumbrance)) {
      double newAmount = subtractMoney(encumbrance.getEncumbrance().getInitialAmountEncumbered(),
        encumbrance.getEncumbrance().getAmountAwaitingPayment(), currency);
      newAmount = subtractMoney(newAmount, encumbrance.getEncumbrance().getAmountExpended(), currency);
      newAmount = sumMoney(newAmount, encumbrance.getEncumbrance().getAmountCredited(), currency);
      encumbrance.setAmount(newAmount);
      newEncumbered = sumMoney(newEncumbered, newAmount, currency);
    } else {
      newEncumbered = sumMoney(newEncumbered, encumbrance.getAmount(), currency);
      newEncumbered = subtractMoney(newEncumbered, existingEncumbrance.getAmount(), currency);
    }
    budget.setEncumbered(newEncumbered);
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
