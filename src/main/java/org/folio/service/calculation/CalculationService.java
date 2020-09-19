package org.folio.service.calculation;

import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;

public class CalculationService {

  public void recalculateOverEncumbered(Budget budget, CurrencyUnit currency) {
    double a = subtractMoneyNonNegative(sumMoney(budget.getAllocated(), budget.getNetTransfers(), currency), budget.getExpenditures(), currency);
    a = subtractMoneyNonNegative(a, budget.getAwaitingPayment(), currency);
    double newOverEncumbrance = subtractMoneyNonNegative(budget.getEncumbered(), a, currency);
    budget.setOverEncumbrance(newOverEncumbrance);
  }

  public void recalculateAvailableUnavailable(Budget budget, Double transactionAmount, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoneyNonNegative(budget.getAvailable(), transactionAmount, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
  }

  public void recalculateBudgetTransfer(Budget budgetFromNew, Transaction transfer, Double netTransferAmount) {
    CurrencyUnit currency = Monetary.getCurrency(transfer.getCurrency());

    double newNetTransfers = subtractMoney(budgetFromNew.getNetTransfers(), netTransferAmount, currency);
    budgetFromNew.setNetTransfers(newNetTransfers);

    recalculateOverEncumbered(budgetFromNew, currency);
    recalculateAvailableUnavailable(budgetFromNew, netTransferAmount, currency);

  }
}
