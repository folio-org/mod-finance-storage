package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.UUID;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.Test;

class PaymentsCreditsTest extends TransactionTest {
  private static final String CREDIT_SAMPLE = "data/transactions-PaymentCredit/credit_CANHIST_30121.json";
  private static final String PAYMENT_SAMPLE = "data/transactions-PaymentCredit/payment_ENDOW-SUBN_30121.json";

  @Test
  void testCreatePaymentsCreditsAllOrNothing() throws MalformedURLException {
    String invoiceId = UUID.randomUUID()
      .toString();
    String orderId = UUID.randomUUID()
      .toString();
    createOrderSummary(orderId, 2);
    createInvoiceSummary(invoiceId, 2);
    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");

    Transaction paymentEncumbranceBefore = jsonTx.mapTo(Transaction.class);
    paymentEncumbranceBefore.getEncumbrance()
      .setSourcePurchaseOrderId(orderId);

    Transaction creditEncumbranceBefore = jsonTx.mapTo(Transaction.class);
    creditEncumbranceBefore.getEncumbrance()
      .setSourcePurchaseOrderId(orderId);
    creditEncumbranceBefore.getEncumbrance()
      .setSourcePoLineId(UUID.randomUUID()
        .toString());
    String fY = paymentEncumbranceBefore.getFiscalYearId();
    String fromFundId = paymentEncumbranceBefore.getFromFundId();

    // prepare budget queries
    String budgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);

    // create 1st Encumbrance, expected number is 2
    String paymentEncumbranceId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(paymentEncumbranceBefore)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    // create 2nd Encumbrance
    String creditEncumbranceId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(creditEncumbranceBefore)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    Budget budgetBefore = getBudgetAndValidate(budgetEndpointWithQueryParams);

    JsonObject paymentjsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentjsonTx.remove("id");

    Transaction payment = paymentjsonTx.mapTo(Transaction.class);
    payment.setSourceInvoiceId(invoiceId);
    payment.setFiscalYearId(fY);
    payment.setFromFundId(fromFundId);
    payment.setPaymentEncumbranceId(paymentEncumbranceId);

    String paymentId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(payment)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    // payment does not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(404);

    JsonObject creditjsonTx = new JsonObject(getFile(CREDIT_SAMPLE));
    creditjsonTx.remove("id");

    Transaction credit = creditjsonTx.mapTo(Transaction.class);
    credit.setSourceInvoiceId(invoiceId);
    credit.setFiscalYearId(fY);
    credit.setToFundId(fromFundId);
    credit.setPaymentEncumbranceId(creditEncumbranceId);

    String creditId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    // 2 transactions(each for a payment and credit) appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), creditId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);

    Transaction paymentEncumbranceAfter = getDataById(TRANSACTION.getEndpointWithId(), paymentEncumbranceId,
        TRANSACTION_TENANT_HEADER).then()
          .statusCode(200)
          .extract()
          .as(Transaction.class);
    Transaction creditEncumbranceAfter = getDataById(TRANSACTION.getEndpointWithId(), creditEncumbranceId,
        TRANSACTION_TENANT_HEADER).then()
          .statusCode(200)
          .extract()
          .as(Transaction.class);

    // Encumbrance Changes for payment
    assertEquals(-payment.getAmount(), subtractValues(paymentEncumbranceAfter.getEncumbrance()
      .getAmountAwaitingPayment(),
        paymentEncumbranceBefore.getEncumbrance()
          .getAmountAwaitingPayment()));
    assertEquals(payment.getAmount(), subtractValues(paymentEncumbranceAfter.getEncumbrance()
      .getAmountExpended(),
        paymentEncumbranceBefore.getEncumbrance()
          .getAmountExpended()));
    assertEquals(-payment.getAmount(), subtractValues(paymentEncumbranceAfter.getAmount(), paymentEncumbranceBefore.getAmount()));

    // Encumbrance Changes for credit
    assertEquals(-credit.getAmount(), subtractValues(creditEncumbranceAfter.getEncumbrance()
      .getAmountExpended(),
        creditEncumbranceBefore.getEncumbrance()
          .getAmountExpended()));

    Budget budgetAfter = getBudgetAndValidate(budgetEndpointWithQueryParams);

    // awaiting payment must decreases by payment amount
    assertEquals(-payment.getAmount(), subtractValues(budgetAfter.getAwaitingPayment(), budgetBefore.getAwaitingPayment()));
    // encumbered must increase by credit amount
    assertEquals(credit.getAmount(), subtractValues(budgetAfter.getEncumbered(), budgetBefore.getEncumbered()));
    // expenditures must increase by payment amt and decrease by credit amount
    assertEquals(subtractValues(payment.getAmount(), credit.getAmount()),
        subtractValues(budgetAfter.getExpenditures(), budgetBefore.getExpenditures()));

  }

  @Test
  void testCreatePaymentsCreditsAllOrNothingWithNoEncumbrances() throws MalformedURLException {
    String invoiceId = UUID.randomUUID()
      .toString();
    createInvoiceSummary(invoiceId, 2);

    JsonObject paymentjsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentjsonTx.remove("id");

    Transaction payment = paymentjsonTx.mapTo(Transaction.class);
    payment.setSourceInvoiceId(invoiceId);
    payment.setPaymentEncumbranceId(null);

    String fY = payment.getFiscalYearId();
    String fromFundId = payment.getFromFundId();

    // prepare budget queries
    String paymentBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    Budget paymentBudgetBefore = getBudgetAndValidate(paymentBudgetEndpointWithQueryParams);

    String paymentId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(payment)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    // payment does not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(404);

    JsonObject creditjsonTx = new JsonObject(getFile(CREDIT_SAMPLE));
    creditjsonTx.remove("id");

    Transaction credit = creditjsonTx.mapTo(Transaction.class);
    credit.setSourceInvoiceId(invoiceId);
    credit.setFiscalYearId(fY);
    credit.setPaymentEncumbranceId(null);
    credit.setAmount(30d);

    String creditBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, credit.getToFundId());
    Budget creditBudgetBefore = getBudgetAndValidate(creditBudgetEndpointWithQueryParams);

    String creditId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    // 2 transactions appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), creditId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);

    Budget paymentBudgetAfter = getBudgetAndValidate(paymentBudgetEndpointWithQueryParams);
    Budget creditBudgetAfter = getBudgetAndValidate(creditBudgetEndpointWithQueryParams);

    // awaiting payment, encumberedmexpenditures must remain same
    assertEquals(0d, subtractValues(paymentBudgetAfter.getAwaitingPayment(), paymentBudgetBefore.getAwaitingPayment()));
    assertEquals(0d, subtractValues(paymentBudgetAfter.getEncumbered(), paymentBudgetBefore.getEncumbered()));
    assertEquals(0d, subtractValues(paymentBudgetAfter.getExpenditures(), paymentBudgetBefore.getExpenditures()));

    // payment changes, available must decrease and unavailable increases
    assertEquals(-payment.getAmount(), subtractValues(paymentBudgetAfter.getAvailable(), paymentBudgetBefore.getAvailable()));
    assertEquals(payment.getAmount(), subtractValues(paymentBudgetAfter.getUnavailable(), paymentBudgetBefore.getUnavailable()));

    // credit changes, available must increase and unavailable decreases
    assertEquals(credit.getAmount(), subtractValues(creditBudgetAfter.getAvailable(), creditBudgetBefore.getAvailable()));
    assertEquals(-credit.getAmount(), subtractValues(creditBudgetAfter.getUnavailable(), creditBudgetBefore.getUnavailable()));

  }

  @Test
  void testCreatePaymentWithoutInvoiceSummary() throws MalformedURLException {
    String invoiceId = UUID.randomUUID()
      .toString();

    JsonObject jsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    jsonTx.remove("id");
    Transaction payment = jsonTx.mapTo(Transaction.class);

    payment.setSourceInvoiceId(invoiceId);

    String transactionSample = JsonObject.mapFrom(payment)
      .encodePrettily();

    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400);

  }

  @Test
  void testPaymentsIdempotentInTemporaryTable() throws MalformedURLException {

    String invoiceId = UUID.randomUUID()
      .toString();
    createInvoiceSummary(invoiceId, 2);

    JsonObject jsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    jsonTx.remove("id");
    Transaction payment = jsonTx.mapTo(Transaction.class);

    payment.setSourceInvoiceId(invoiceId);
    String transactionSample = JsonObject.mapFrom(payment)
      .encodePrettily();

    // to support retrying a payment , just id is updated and a duplicate entry is not created
    String id = postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class)
      .getId();

    String retryId = postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class)
      .getId();

    assertNotEquals(retryId, id);

  }

  @Test
  void testPaymentsWithInvalidPaymentEncumbranceInTemporaryTable() throws MalformedURLException {

    String invoiceId = UUID.randomUUID()
      .toString();
    createInvoiceSummary(invoiceId, 2);

    JsonObject jsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    jsonTx.remove("id");
    Transaction payment = jsonTx.mapTo(Transaction.class);

    payment.setSourceInvoiceId(invoiceId);
    payment.setPaymentEncumbranceId(UUID.randomUUID()
      .toString());
    String transactionSample = JsonObject.mapFrom(payment)
      .encodePrettily();

    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400);

  }

}
