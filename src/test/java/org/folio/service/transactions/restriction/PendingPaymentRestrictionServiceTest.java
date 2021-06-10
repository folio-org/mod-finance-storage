package org.folio.service.transactions.restriction;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import javax.money.MonetaryAmount;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PendingPaymentRestrictionServiceTest {

  @InjectMocks
  private PendingPaymentRestrictionService restrictionService;

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
  void TestHandleValidationErrorExceptionThrown() {
    HttpException thrown = assertThrows(
      HttpException.class,
      () -> restrictionService.handleValidationError(new Transaction()),
      "Expected handleValidationError() to throw, but it didn't"
    );

    Parameter parameter = new Parameter().withKey("fromFundId")
      .withValue("null");
    Error error = new Error().withCode("-1")
      .withMessage("may not be null")
      .withParameters(Collections.singletonList(parameter));
    Errors errors = new Errors().withErrors(Collections.singletonList(error)).withTotalRecords(1);
    assertThat(thrown.getStatusCode(), is(422));
    assertThat(thrown.getPayload(), is(JsonObject.mapFrom(errors).encode()));
  }

  @Test
  void testHandleValidationErrorValidTransaction() {
    assertNull(restrictionService.handleValidationError(new Transaction().withFromFundId(fundId)));
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

  @Test
  void testIsTransactionOverspendRestrictedWithEmptyAllowableExpenditure() {
    Assertions.assertFalse(restrictionService.isTransactionOverspendRestricted(new Ledger().withRestrictExpenditures(true), budget.withAllowableExpenditure(null)));
  }

  @Test
  void testIsTransactionOverspendRestrictedWithRestrictExpendituresIsFalse() {
    Assertions.assertFalse(restrictionService.isTransactionOverspendRestricted(new Ledger().withRestrictExpenditures(false), budget.withAllowableExpenditure(110d)));
  }

  @Test
  void testIsTransactionOverspendRestrictedWithRestrictExpendituresIsTrueWithSpecifiedAllowableExpenditure() {
    Assertions.assertTrue(restrictionService.isTransactionOverspendRestricted(new Ledger().withRestrictExpenditures(true), budget.withAllowableExpenditure(110d)));
  }
}
