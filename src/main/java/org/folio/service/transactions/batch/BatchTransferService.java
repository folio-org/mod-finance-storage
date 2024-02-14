package org.folio.service.transactions.batch;

import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.utils.CalculationUtils;

import java.util.List;

import static org.folio.rest.jaxrs.model.Transaction.TransactionType.TRANSFER;
import static org.folio.utils.CalculationUtils.calculateBudgetSummaryFields;

public class BatchTransferService extends AbstractBatchTransactionService {

  @Override
  public Transaction.TransactionType getTransactionType() {
    return TRANSFER;
  }

  @Override
  public void updatesForCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder) {
    calculateBudgetsTotals(transactionsToCreate, holder);
  }

  @Override
  public void updatesForUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder) {
    throw new HttpException(400, "Transfer updates are not implemented.");
  }

  private void calculateBudgetsTotals(List<Transaction> transactions, BatchTransactionHolder holder) {
    createBudgetMapForAllocationsAndTransfers(transactions, holder.getBudgets())
      .forEach((budget, budgetTransactions) -> {
        budgetTransactions.forEach(tr -> applyTransaction(budget, tr));
        calculateBudgetSummaryFields(budget);
        updateBudgetMetadata(budget, budgetTransactions.get(0));
      });
  }

  private void applyTransaction(Budget budget, Transaction transfer) {
    if (budget.getFundId().equals(transfer.getFromFundId())) {
      CalculationUtils.recalculateBudgetTransfer(budget, transfer, transfer.getAmount());
    } else if (budget.getFundId().equals(transfer.getToFundId())) {
      CalculationUtils.recalculateBudgetTransfer(budget, transfer, -transfer.getAmount());
    }
  }

}
