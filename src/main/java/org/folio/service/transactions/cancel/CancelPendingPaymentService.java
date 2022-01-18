package org.folio.service.transactions.cancel;

import io.vertx.core.json.JsonObject;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.service.budget.BudgetService;

import java.util.List;
import java.util.Map;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class CancelPendingPaymentService extends CancelTransactionService{
  public CancelPendingPaymentService(TransactionDAO transactionsDAO, BudgetService budgetService) {
    super(transactionsDAO, budgetService);
  }

  @Override
  Budget cancelBudget(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      entry.getValue()
        .forEach(tmpTransaction -> {
          double newExpenditures = budget.getExpenditures() - tmpTransaction.getAmount();
          double newVoidedAmount = tmpTransaction.getAmount();

          budget.setExpenditures(newExpenditures);
          tmpTransaction.setVoidedAmount(newVoidedAmount);
          tmpTransaction.setAmount(0.0);
          budgetService.updateBudgetMetadata(budget, tmpTransaction);
        });
    }
    return budget;
  }
}
