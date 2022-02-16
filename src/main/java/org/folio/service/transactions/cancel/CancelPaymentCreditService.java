package org.folio.service.transactions.cancel;

import io.vertx.core.json.JsonObject;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.service.budget.BudgetService;
import org.folio.utils.MoneyUtils;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.List;
import java.util.Optional;

public class CancelPaymentCreditService extends CancelTransactionService {

  public CancelPaymentCreditService(BudgetService budgetService, TransactionDAO paymentCreditDAO, TransactionDAO encumbranceDAO) {
    super(budgetService, paymentCreditDAO, encumbranceDAO);
  }

  @Override
  protected Budget budgetMoneyBack(Budget budget, List<Transaction> transactions) {
    Budget newBudget = JsonObject.mapFrom(budget).mapTo(Budget.class);
    CurrencyUnit currency = Monetary.getCurrency(transactions.get(0).getCurrency());
    transactions.forEach(tmpTransaction -> {
      double newExpenditures;
      if (tmpTransaction.getTransactionType().equals(TransactionType.CREDIT)) {
        newExpenditures = MoneyUtils.sumMoney(newBudget.getExpenditures(),
          tmpTransaction.getAmount(), currency);
      } else {
        newExpenditures = MoneyUtils.subtractMoney(newBudget.getExpenditures(),
          tmpTransaction.getAmount(), currency);
      }
      double newVoidedAmount = tmpTransaction.getAmount();
      newBudget.setExpenditures(newExpenditures);
      tmpTransaction.setVoidedAmount(newVoidedAmount);
      tmpTransaction.setAmount(0.0);
    });
    return newBudget;
  }

  @Override
  protected Optional<String> getEncumbranceId(Transaction pendingPayment) {
    return Optional.ofNullable(pendingPayment.getPaymentEncumbranceId());
  }

  @Override
  protected void cancelEncumbrance(Transaction encumbrance, List<Transaction> paymentsAndCredits) {
    CurrencyUnit currency = Monetary.getCurrency(encumbrance.getCurrency());
    double newAmount = encumbrance.getEncumbrance().getAmountExpended();
    for (Transaction paymentOrCredit : paymentsAndCredits) {
      if (paymentOrCredit.getTransactionType().equals(TransactionType.CREDIT)) {
        newAmount = MoneyUtils.sumMoney(newAmount, paymentOrCredit.getAmount(), currency);
      } else {
        newAmount = MoneyUtils.subtractMoney(newAmount, paymentOrCredit.getAmount(), currency);
      }
    }
    encumbrance.getEncumbrance().setAmountExpended(newAmount);
  }
}
