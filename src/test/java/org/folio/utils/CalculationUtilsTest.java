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
    "1800, 200, 1500, 500, 200, 200",
    "1800, 200, 1500, 400, 100, 0",
    "1800, 200, 1500, 700, 200, 200",
    "1800, 200, 1000, 100, 200, 0",
    "1800, 200, 1000, 1000, 200, 200",
    "0, 2500, 0, 250, 2500, 250",
    "4000, 100, 4100, 409, 1, 1",
    "4000, 100, 4200, 409, 1, 1",
    "80, 20, 100, 0, 1, 1",
    "80, 20, 100, 1, 0, 1",
    "100, 0, 70, 20, 30, 20",
    "100, 0, 0, 110, 10, 10",
    "100, 0, 0, -13, 1, 0",
    "100, 0, 0, -13, 15, 0",
    "100, 0, 0, -13, 110, 0",
    "100, 0, 0, -13, 0, 0",
    "100, 0, 0, -13, 20, 0",
    "100, 0, -90, 107, 90, 7",
    "100, 0, -90, 192, 5, 5",
    "100, 0, -90, 17, 5, 0",
    "10000, 0, 1500, 1500, 10000, 1500"
  })
  void overEncumberedShouldBeGreaterThenZeroWhenEncumberedGreaterThatTotalFunding(double allocated, double netTransfer,
                    double expended, double awaitingPayment, double encumbered, double expOverEncumbered) {
    Budget budget = new Budget()
      .withInitialAllocation(allocated)
      .withNetTransfers(netTransfer)
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

    CalculationUtils.recalculateBudgetAllocationTo(budget, allocation, 100d);

    assertEquals(100d, budget.getAllocationTo(), 0d);
  }
}
