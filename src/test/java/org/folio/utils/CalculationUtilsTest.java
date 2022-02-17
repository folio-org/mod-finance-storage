package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.rest.jaxrs.model.Budget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    "1800, 200, 1500, 600, 200, 200",
    "1800, 200, 1000, 100, 2000, 0",
    "1800, 200, 1000, 1000, 2000, 0",
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
}
