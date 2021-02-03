package org.folio.service.transactions.restriction;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import javax.money.MonetaryAmount;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PaymentCreditRestrictionServiceTest {

  @InjectMocks
  private PaymentCreditRestrictionService restrictionService;

  private String fundId = UUID.randomUUID().toString();
  private String fiscalYearId = UUID.randomUUID().toString();
  private Budget budget;
  private String currency = "USD";

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.openMocks(this);

    budget = new Budget()
      .withFiscalYearId(fiscalYearId)
      .withAwaitingPayment(0d)
      .withAllocated(100d)
      .withAvailable(100d)
      .withEncumbered(0d)
      .withUnavailable(0d)
      .withFundId(fundId);
  }

  @Test
  void testGetBudgetRemainingAmountForEncumbrance() {
    budget.withNetTransfers(20d)
      .withAllowableExpenditure(110d)
      .withEncumbered(10d)
      .withAwaitingPayment(11d)
      .withExpenditures(90d)
      .withAvailable(21d) // should not be used
      .withUnavailable(22d); // should not be used
    MonetaryAmount amount = restrictionService.getBudgetRemainingAmount(budget, currency, new Transaction().withAmount(5d));
    assertThat(amount.getNumber().doubleValue(), is(26d));
  }

}
