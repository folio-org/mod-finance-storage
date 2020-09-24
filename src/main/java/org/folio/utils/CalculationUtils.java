package org.folio.utils;

import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.utils.MoneyUtils.sumMoney;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;

public final class CalculationUtils {

  private CalculationUtils() {}

  public static void recalculateOverEncumbered(Budget budget, CurrencyUnit currency) {
    double a = subtractMoneyNonNegative(sumMoney(budget.getAllocated(), budget.getNetTransfers(), currency), budget.getExpenditures(), currency);
    a = subtractMoneyNonNegative(a, budget.getAwaitingPayment(), currency);
    double newOverEncumbrance = subtractMoneyNonNegative(budget.getEncumbered(), a, currency);
    budget.setOverEncumbrance(newOverEncumbrance);
  }

  public static void recalculateOverExpended(Budget budget, CurrencyUnit currency) {
    double a = subtractMoneyNonNegative(sumMoney(budget.getAllocated(), budget.getNetTransfers(), currency), budget.getEncumbered(), currency);
    a = subtractMoneyNonNegative(a, budget.getExpenditures(), currency);
    double newOverExpended = subtractMoneyNonNegative(budget.getAwaitingPayment(), a, currency);
    budget.setOverExpended(newOverExpended);
  }

  public static void recalculateAvailableUnavailable(Budget budget, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double maxAvailable = sumMoney(budget.getAllocated(), budget.getNetTransfers(), currency);
    double newAvailable = subtractMoneyNonNegative(maxAvailable, newUnavailable, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
  }

  public static void recalculateBudgetTransfer(Budget budgetFromNew, Transaction transfer, Double netTransferAmount) {
    CurrencyUnit currency = Monetary.getCurrency(transfer.getCurrency());

    double newNetTransfers = subtractMoney(budgetFromNew.getNetTransfers(), netTransferAmount, currency);
    budgetFromNew.setNetTransfers(newNetTransfers);

    recalculateOverEncumbered(budgetFromNew, currency);
    recalculateAvailableUnavailable(budgetFromNew, currency);

  }
}
