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

public class CancelPendingPaymentService extends CancelTransactionService {

  public CancelPendingPaymentService(BudgetService budgetService, TransactionDAO paymentCreditDAO, TransactionDAO encumbranceDAO) {
    super(budgetService, paymentCreditDAO, encumbranceDAO);
  }

  @Override
  Budget budgetMoneyBack(Budget budget, List<Transaction> transactions) {
    Budget newBudget = JsonObject.mapFrom(budget).mapTo(Budget.class);
    CurrencyUnit currency = Monetary.getCurrency(transactions.get(0).getCurrency());
    transactions.forEach(tmpTransaction -> {
      double newAwaitingPayment = MoneyUtils.subtractMoney(newBudget.getAwaitingPayment(),
        tmpTransaction.getAmount(), currency);
      double newVoidedAmount = tmpTransaction.getAmount();

      newBudget.setAwaitingPayment(newAwaitingPayment);
      tmpTransaction.setVoidedAmount(newVoidedAmount);
      tmpTransaction.setAmount(0.0);
    });
    return newBudget;
  }

  @Override
  String getEncumbranceId(Transaction pendingPayment) {
    if (pendingPayment.getAwaitingPayment() == null)
      return null;
    return pendingPayment.getAwaitingPayment().getEncumbranceId();
  }

  @Override
  void cancelEncumbrance(Transaction encumbrance, List<Transaction> pendingPayments) {
    CurrencyUnit currency = Monetary.getCurrency(encumbrance.getCurrency());
    double newAmount = encumbrance.getEncumbrance().getAmountAwaitingPayment();
    for (Transaction pendingPayment : pendingPayments) {
      newAmount = MoneyUtils.subtractMoney(newAmount, pendingPayment.getAmount(), currency);
    }
    encumbrance.getEncumbrance().setAmountAwaitingPayment(newAmount);
  }
}
