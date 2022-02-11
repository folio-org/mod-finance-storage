package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.rest.jaxrs.model.Budget;
import org.junit.jupiter.api.Test;

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

  @Test
  void overEncumberedShouldBeGreaterThenZeroWhenEncumberedGreaterThatTotalFunding() {
    Budget budget = new Budget()
      .withInitialAllocation(1800d)
      .withNetTransfers(200d)
      .withExpenditures(1500d)
      .withAwaitingPayment(500d)
      .withEncumbered(200d);

    CalculationUtils.calculateBudgetSummaryFields(budget);

    assertEquals(200d, budget.getOverEncumbrance(), 0d);

  }
}
