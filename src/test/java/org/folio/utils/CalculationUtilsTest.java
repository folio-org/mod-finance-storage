package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

public class CalculationUtilsTest {

    @Test
    void overExpendedShouldBeGreaterThenZeroWhenSumOfExpendedAwaitingPaymentGreaterThatTotalFunding() {
        Budget budget = new Budget()
                .withInitialAllocation(30d)
                .withAllocationTo(55d)
                .withNetTransfers(-3d)
                .withExpenditures(90d);

        CalculationUtils.calculateBudgetSummaryFields(budget);

        assertEquals(8d, budget.getOverExpended(), 0d);
    }

  @ParameterizedTest
  @CsvSource({
    "1800, 200, 1500, 100, 700, 200, 300",
    "1800, 200, 1500, 100, 500, 200, 200",
    "1800, 200, 1500, 0, 400, 100, 0",
    "1800, 200, 1500, 200, 700, 200, 400",
    "1800, 200, 1000, 0, 100, 200, 0",
    "1800, 200, 1000, 0, 1000, 200, 200",
    "0, 2500, 0, 0, 250, 2500, 250",
    "4000, 100, 4100, 0, 409, 1, 1",
    "4000, 100, 4200, 0.5, 409, 1, 1.5",
    "80, 20, 100, 0, 0, 1, 1",
    "80, 20, 100, 0, 1, 0, 1",
    "100, 0, 70, 5, 20, 30, 20",
    "100, 0, 0, 10, 110, 10, 20",
    "100, 0, 0, 0, -13, 1, 0",
    "100, 0, 0, 0, -13, 15, 0",
    "100, 0, 0, 0, -13, 110, 0",
    "100, 0, 0, 0, -13, 0, 0",
    "100, 0, 0, 0, -13, 20, 0",
    "100, 0, -90, 1, 107, 90, 7",
    "100, 0, -90, 0, 192, 5, 5",
    "100, 0, -90, 0, 17, 5, 0",
    "10000, 0, 1500, 500, 1500, 10000, 1500",
    "0, 1500, 0, 0, 150, 1500, 150"
  })
  void overEncumberedShouldBeGreaterThenZeroWhenEncumberedGreaterThatTotalFunding(double allocated, double netTransfer,
                    double expended, double credited, double awaitingPayment, double encumbered, double expOverEncumbered) {
    Budget budget = new Budget()
      .withInitialAllocation(allocated)
      .withNetTransfers(netTransfer)
      .withCredits(credited)
      .withExpenditures(expended)
      .withAwaitingPayment(awaitingPayment)
      .withEncumbered(encumbered);

    CalculationUtils.calculateBudgetSummaryFields(budget);

    assertEquals(expOverEncumbered, budget.getOverEncumbrance(), 0d);
  }


  @Test
  void overEncumberedShouldBeGreaterThenZeroWhenEncumberedAndOverExpendedGreaterThatTotalFunding() {
    Budget budget = new Budget()
      .withInitialAllocation(1800d)
      .withNetTransfers(200d)
      .withExpenditures(1500d)
      .withAwaitingPayment(600d)
      .withEncumbered(200d);

    CalculationUtils.calculateBudgetSummaryFields(budget);

    assertEquals(200d, budget.getOverEncumbrance(), 0d);
  }

  @Test
  void allocationShouldBeAddToAllocationToIfFromFundIdExist() {
    Budget budget = new Budget()
      .withInitialAllocation(0d)
      .withAllocationTo(0d);

    Transaction allocation = new Transaction()
      .withAmount(100d)
      .withFromFundId(UUID.randomUUID().toString())
      .withCurrency("USD");

    CalculationUtils.recalculateBudgetAllocationTo(budget, allocation);

    assertEquals(100d, budget.getAllocationTo(), 0d);
  }
}
