package org.folio.service.transactions.cancel;

import io.vertx.core.json.JsonObject;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.service.budget.BudgetService;
import org.folio.utils.MoneyUtils;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.List;
import java.util.Map;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class CancelPendingPaymentService extends CancelTransactionService {

  public CancelPendingPaymentService(TransactionDAO transactionsDAO, BudgetService budgetService) {
    super(transactionsDAO, budgetService);
  }

  @Override
  Budget budgetMoneyBack(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          double newAwaitingPayment = MoneyUtils.subtractMoney(budget.getAwaitingPayment(),
            tmpTransaction.getAmount(), currency);
          double newVoidedAmount = tmpTransaction.getAmount();

          budget.setAwaitingPayment(newAwaitingPayment);
          tmpTransaction.setVoidedAmount(newVoidedAmount);
          tmpTransaction.setAmount(0.0);
        });
    }
    return budget;
  }
}
