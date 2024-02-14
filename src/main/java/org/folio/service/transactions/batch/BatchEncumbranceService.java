package org.folio.service.transactions.batch;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.List;
import java.util.Map;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.utils.CalculationUtils.calculateBudgetSummaryFields;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

public class BatchEncumbranceService extends AbstractBatchTransactionService {

  @Override
  public TransactionType getTransactionType() {
    return TransactionType.ENCUMBRANCE;
  }

  @Override
  public void updatesForCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder) {
    Map<Budget, List<Transaction>> budgetToTransactions = createBudgetMapForTransactions(transactionsToCreate,
      holder.getBudgets());
    budgetToTransactions.forEach(this::updateBudgetTotalsForCreation);
  }

  @Override
  public void updatesForUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder) {
    Map<Budget, List<Transaction>> budgetToTransactions = createBudgetMapForTransactions(transactionsToUpdate,
      holder.getBudgets());
    budgetToTransactions.forEach((budget, transactions) -> updateBudgetTotalsForUpdate(budget, transactions,
      holder.getExistingTransactionMap()));
  }

  private void updateBudgetTotalsForCreation(Budget budget, List<Transaction> transactions) {
    CurrencyUnit currency = Monetary.getCurrency(transactions.get(0).getCurrency());
    transactions.forEach(transaction -> {
      double newEncumbered = sumMoney(budget.getEncumbered(), transaction.getAmount(), currency);
      budget.setEncumbered(newEncumbered);
    });
    updateBudgetMetadata(budget, transactions.get(0));
  }

  private void updateBudgetTotalsForUpdate(Budget budget, List<Transaction> transactions,
      Map<String, Transaction> existingTransactions) {
    if (isNotEmpty(transactions)) {
      CurrencyUnit currency = Monetary.getCurrency(transactions.get(0).getCurrency());
      transactions.forEach(transaction -> {
        Transaction existingTransaction = existingTransactions.get(transaction.getId());
        if (existingTransaction != null && isNotFromReleasedExceptToUnreleased(transaction, existingTransaction)) {
          processBudget(budget, currency, transaction, existingTransaction);
        }
      });
      calculateBudgetSummaryFields(budget);
      updateBudgetMetadata(budget, transactions.get(0));
    }
  }

  private void processBudget(Budget budget, CurrencyUnit currency, Transaction transaction, Transaction existingTransaction) {
    double newEncumbered = budget.getEncumbered();
    if (isEncumbranceReleased(transaction)) {
      newEncumbered = subtractMoney(newEncumbered, existingTransaction.getAmount(), currency);
      transaction.setAmount(0d);
    } else  if (isTransitionFromUnreleasedToPending(transaction, existingTransaction)) {
      transaction.setAmount(0d);
      transaction.getEncumbrance().setInitialAmountEncumbered(0d);
      newEncumbered = subtractMoney(newEncumbered, existingTransaction.getAmount(), currency);
    } else if (isTransitionFromPendingToUnreleased(transaction, existingTransaction)) {
      double newAmount = subtractMoney(transaction.getEncumbrance().getInitialAmountEncumbered(), existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
      newAmount = subtractMoney(newAmount, existingTransaction.getEncumbrance().getAmountExpended(), currency);
      transaction.setAmount(newAmount);
      newEncumbered = sumMoney(currency, newEncumbered, newAmount);
    } else if (isTransitionFromReleasedToUnreleased(transaction, existingTransaction)) {
      double newAmount = subtractMoney(transaction.getEncumbrance().getInitialAmountEncumbered(),
        transaction.getEncumbrance().getAmountAwaitingPayment(), currency);
      newAmount = subtractMoney(newAmount, transaction.getEncumbrance().getAmountExpended(), currency);
      transaction.setAmount(newAmount);
      newEncumbered = sumMoney(newEncumbered, newAmount, currency);
    } else {
      newEncumbered = sumMoney(newEncumbered, transaction.getAmount(), currency);
      newEncumbered = subtractMoney(newEncumbered, existingTransaction.getAmount(), currency);
    }
    budget.setEncumbered(newEncumbered);
  }


  private boolean isEncumbranceReleased(Transaction transaction) {
    return transaction.getEncumbrance().getStatus() == Encumbrance.Status.RELEASED;
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
