package org.folio.service.transactions.batch;

import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.utils.CalculationUtils;

import java.util.List;
import java.util.Map;

import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;

public class BatchAllocationService extends AbstractBatchTransactionService {

  @Override
  public Transaction.TransactionType getTransactionType() {
    return ALLOCATION;
  }

  @Override
  public void updatesForCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder) {
    calculateBudgetsTotals(transactionsToCreate, holder);
  }

  @Override
  public void updatesForUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder) {
    throw new HttpException(400, "Allocation updates are not implemented.");
  }

  private void calculateBudgetsTotals(List<Transaction> transactions, BatchTransactionHolder holder) {
    Map<Budget, List<Transaction>> budgetToTransactions = createBudgetMapForAllocationsAndTransfers(transactions, holder.getBudgets());
    if (!budgetToTransactions.isEmpty()) {
      budgetToTransactions.forEach(this::updateBudgetTotals);
    }
  }

  private void updateBudgetTotals(Budget budget, List<Transaction> transactions) {
    for (Transaction transaction : transactions) {
      applyTransaction(budget, transaction);
    }
    updateBudgetMetadata(budget, transactions.get(0));
  }

  private void applyTransaction(Budget budget, Transaction allocation) {
    if (budget.getFundId().equals(allocation.getFromFundId())) {
      CalculationUtils.recalculateBudgetAllocationFrom(budget, allocation);
    } else if (budget.getFundId().equals(allocation.getToFundId())) {
      CalculationUtils.recalculateBudgetAllocationTo(budget, allocation);
    }
  }

}
