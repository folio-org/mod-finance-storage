package org.folio.service.transactions.cancel;

import io.vertx.core.json.JsonObject;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.service.budget.BudgetService;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.List;
import java.util.Map;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.utils.MoneyUtils.sumMoney;

public class CancelPendingPaymentService extends CancelTransactionService{
  public CancelPendingPaymentService(TransactionDAO transactionsDAO, BudgetService budgetService) {
    super(transactionsDAO, budgetService);
  }

  @Override Budget cancelBudget(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getAmount(), currency);
          budget.setAwaitingPayment(newAwaitingPayment);
          budgetService.updateBudgetMetadata(budget, tmpTransaction);
          budgetService.clearReadOnlyFields(budget);
        });
    }
    return budget;
  }
}
