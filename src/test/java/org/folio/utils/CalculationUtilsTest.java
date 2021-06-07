package org.folio.utils;

import org.folio.rest.jaxrs.model.Budget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

public class CalculationUtilsTest {

    @Test
    void overExpendedShouldBeGreaterThenZeroWhenSumOfExpendedAwaitingPaymentGreaterThatTotalFunding() {
        Budget budget = new Budget()
                .withInitialAllocation(30d)
                .withAllocationTo(55d)
                .withNetTransfers(-3d)
                .withExpenditures(90d);

        CalculationUtils.calculateBudgetSummaryFields(budget);

        Assertions.assertEquals(8d, budget.getOverExpended(), 0d);

    }
}
