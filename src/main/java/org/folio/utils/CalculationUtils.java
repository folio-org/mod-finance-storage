package org.folio.utils;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

import java.math.BigDecimal;
import java.util.Map;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;

public final class CalculationUtils {

  private static final String ALLOCATED = "allocated";
  private static final String AVAILABLE = "available";
  private static final String UNAVAILABLE = "unavailable";
  private static final String OVER_ENCUMBERED = "overEncumbered";
  private static final String OVER_EXPENDED = "overExpended";
  private static final String TOTAL_FUNDING = "totalFunding";
  private static final String CACHE_BALANCE = "cacheBalance";

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
    Map<String, BigDecimal> result = calculate(budget.getInitialAllocation(),
      budget.getAllocationFrom(),
      budget.getAllocationTo(),
      budget.getNetTransfers(),
      budget.getExpenditures(),
      budget.getEncumbered(),
      budget.getAwaitingPayment()
    );

    budget.setAllocated(result.get(ALLOCATED).doubleValue());
    budget.setAvailable(result.get(AVAILABLE).doubleValue());
    budget.setUnavailable(result.get(UNAVAILABLE).doubleValue());
    budget.setOverEncumbrance(result.get(OVER_ENCUMBERED).doubleValue());
    budget.setOverExpended(result.get(OVER_EXPENDED).doubleValue());
    budget.setTotalFunding(result.get(TOTAL_FUNDING).doubleValue());
    budget.setCashBalance(result.get(CACHE_BALANCE).doubleValue());
  }

  public static void calculateBudgetSummaryFields(LedgerFiscalYearRolloverBudget rolloverBudget) {
    Map<String, BigDecimal> result = calculate(rolloverBudget.getInitialAllocation(),
      rolloverBudget.getAllocationFrom(),
      rolloverBudget.getAllocationTo(),
      rolloverBudget.getNetTransfers(),
      rolloverBudget.getExpenditures(),
      rolloverBudget.getEncumbered(),
      rolloverBudget.getAwaitingPayment()
    );

    rolloverBudget.setAllocated(result.get(ALLOCATED).doubleValue());
    rolloverBudget.setAvailable(result.get(AVAILABLE).doubleValue());
    rolloverBudget.setUnavailable(result.get(UNAVAILABLE).doubleValue());
    rolloverBudget.setOverEncumbrance(result.get(OVER_ENCUMBERED).doubleValue());
    rolloverBudget.setOverExpended(result.get(OVER_EXPENDED).doubleValue());
    rolloverBudget.setTotalFunding(result.get(TOTAL_FUNDING).doubleValue());
    rolloverBudget.setCashBalance(result.get(CACHE_BALANCE).doubleValue());
  }

  private static Map<String, BigDecimal> calculate(Double dInitialAllocation,
                                            Double dAllocationFrom,
                                            Double dAllocationTo,
                                            Double dNetTransfers,
                                            Double dExpenditures,
                                            Double dEncumbered,
                                            Double dAwaitingPayment) {
    BigDecimal initialAllocation = BigDecimal.valueOf(dInitialAllocation);
    BigDecimal allocationFrom = BigDecimal.valueOf(dAllocationFrom);
    BigDecimal allocationTo = BigDecimal.valueOf(dAllocationTo);

    BigDecimal netTransfers = BigDecimal.valueOf(dNetTransfers);
    BigDecimal expended = BigDecimal.valueOf(dExpenditures);
    BigDecimal encumbered = BigDecimal.valueOf(dEncumbered);
    BigDecimal awaitingPayment = BigDecimal.valueOf(dAwaitingPayment);

    BigDecimal allocated = initialAllocation.add(allocationTo).subtract(allocationFrom);
    BigDecimal unavailable = encumbered.add(awaitingPayment).add(expended);
    BigDecimal totalFunding = allocated.add(netTransfers);
    BigDecimal cashBalance = totalFunding.subtract(expended);
    BigDecimal available = totalFunding.subtract(unavailable).max(BigDecimal.ZERO);
    BigDecimal overExpended = expended.add(awaitingPayment).subtract(totalFunding.max(BigDecimal.ZERO)).max(BigDecimal.ZERO);

    BigDecimal overEncumbered = calculateOverEncumbered(encumbered, unavailable, totalFunding, overExpended, awaitingPayment, expended);

    return Map.of(ALLOCATED, allocated,
      UNAVAILABLE, unavailable,
      TOTAL_FUNDING, totalFunding,
      CACHE_BALANCE, cashBalance,
      AVAILABLE, available,
      OVER_EXPENDED, overExpended,
      OVER_ENCUMBERED, overEncumbered);
  }

  private static BigDecimal calculateOverEncumbered(BigDecimal encumbered, BigDecimal unavailable,
            BigDecimal totalFunding, BigDecimal overExpended, BigDecimal awaitingPayment, BigDecimal expended) {
    BigDecimal overCommitted = unavailable.subtract(totalFunding);
    if (overCommitted.compareTo(BigDecimal.ZERO) > 0) {
      if (encumbered.compareTo(BigDecimal.ZERO) == 0 || totalFunding.compareTo(encumbered) == 0) {
        return awaitingPayment;
      } else if (encumbered.compareTo(BigDecimal.ZERO) == 0 && expended.compareTo(totalFunding) == 0) {
        return awaitingPayment;
      } else if (awaitingPayment.compareTo(BigDecimal.ZERO) >= 0) {
        return overCommitted.subtract(overExpended);
      }
    }
    return BigDecimal.ZERO;
  }
}
