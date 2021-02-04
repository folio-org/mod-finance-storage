package org.folio.utils;

import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

import java.math.BigDecimal;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;

public final class CalculationUtils {

  private CalculationUtils() {}

  public static void recalculateBudgetTransfer(Budget budgetFromNew, Transaction transfer, Double netTransferAmount) {
    CurrencyUnit currency = Monetary.getCurrency(transfer.getCurrency());

    double newNetTransfers = subtractMoney(budgetFromNew.getNetTransfers(), netTransferAmount, currency);
    budgetFromNew.setNetTransfers(newNetTransfers);
  }

  public static void recalculateBudgetAllocationFrom(Budget budget, Transaction allocation, Double allocatedAmount) {
    CurrencyUnit currency = Monetary.getCurrency(allocation.getCurrency());
    double newAllocation = sumMoney(budget.getAllocationFrom(), allocatedAmount, currency);
    budget.setAllocationFrom(newAllocation);
  }

  public static void recalculateBudgetAllocationTo(Budget budget, Transaction allocation, Double allocatedAmount) {
    CurrencyUnit currency = Monetary.getCurrency(allocation.getCurrency());
    double newAllocation = sumMoney(budget.getAllocationTo(), allocatedAmount, currency);
    if (budget.getInitialAllocation() > 0) {
      budget.setAllocationTo(newAllocation);
    } else {
      budget.setInitialAllocation(allocatedAmount);
    }
  }

  public static void calculateBudgetSummaryFields(Budget budget) {
    BigDecimal initialAllocation = BigDecimal.valueOf(budget.getInitialAllocation());
    BigDecimal allocationFrom = BigDecimal.valueOf(budget.getAllocationFrom());
    BigDecimal allocationTo = BigDecimal.valueOf(budget.getAllocationTo());

    BigDecimal netTransfers = BigDecimal.valueOf(budget.getNetTransfers());
    BigDecimal expended = BigDecimal.valueOf(budget.getExpenditures());
    BigDecimal encumbered = BigDecimal.valueOf(budget.getEncumbered());
    BigDecimal awaitingPayment = BigDecimal.valueOf(budget.getAwaitingPayment());

    BigDecimal allocated = initialAllocation.add(allocationTo).subtract(allocationFrom);
    BigDecimal unavailable = encumbered.add(awaitingPayment).add(expended);
    BigDecimal totalFunding = allocated.add(netTransfers);
    BigDecimal cashBalance = totalFunding.subtract(expended);
    BigDecimal available = totalFunding.subtract(unavailable).max(BigDecimal.ZERO);
    BigDecimal overEncumbered = encumbered.subtract(totalFunding.max(BigDecimal.ZERO)).max(BigDecimal.ZERO);
    BigDecimal overExpended = expended.add(awaitingPayment).subtract(totalFunding.max(BigDecimal.ZERO)).max(BigDecimal.ZERO);

    budget.setAllocated(allocated.doubleValue());
    budget.setAvailable(available.doubleValue());
    budget.setUnavailable(unavailable.doubleValue());
    budget.setOverEncumbrance(overEncumbered.doubleValue());
    budget.setOverExpended(overExpended.doubleValue());
    budget.setTotalFunding(totalFunding.doubleValue());
    budget.setCashBalance(cashBalance.doubleValue());
  }
}
