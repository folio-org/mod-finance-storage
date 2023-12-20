package org.folio.rest.impl;

import static org.folio.rest.impl.EncumbrancesTest.ENCUMBRANCE_SAMPLE;
import static org.folio.rest.impl.TransactionTest.BUDGETS;
import static org.folio.rest.impl.TransactionTest.BUDGETS_QUERY;
import static org.folio.rest.impl.TransactionTest.TRANSACTION_ENDPOINT;
import static org.folio.rest.impl.TransactionTest.TRANSACTION_TENANT_HEADER;
import static org.folio.rest.impl.TransactionsSummariesTest.INVOICE_TRANSACTION_SUMMARIES_ENDPOINT;
import static org.folio.rest.impl.TransactionsSummariesTest.ORDER_TRANSACTION_SUMMARIES_ENDPOINT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.ALLOCATION_TRANSACTION;
import static org.folio.service.transactions.AllOrNothingTransactionService.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;
import static org.folio.service.transactions.restriction.BaseTransactionRestrictionService.FUND_CANNOT_BE_PAID;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import java.util.stream.Stream;

import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class PaymentsCreditsTest extends TestBase {
  private static final String CREDIT_SAMPLE = "data/transactions/credits/credit_CANHIST_30121.json";
  private static final String PAYMENT_SAMPLE = "data/transactions/payments/payment_ENDOW-SUBN_30121.json";
  private static final String TRANSACTION_SAMPLE = "data/transactions/encumbrances/encumbrance_ENDOW-SUBN_S60402_80.json";
  private static TenantJob tenantJob;

  @BeforeEach
  void prepareData() {
    tenantJob = prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
  }

  @AfterEach
  void cleanupData() {
    purge(TRANSACTION_TENANT_HEADER);
  }

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreatePaymentsCreditsAllOrNothing() {

    String invoiceId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
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

    JsonObject paymentJsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentJsonTx.remove("id");

    Transaction payment = paymentJsonTx.mapTo(Transaction.class);
    payment.setSourceInvoiceId(invoiceId);
    payment.setFiscalYearId(fY);
    payment.setFromFundId(fromFundId);
    payment.setPaymentEncumbranceId(paymentEncumbranceId);

    Transaction pendingPaymentForPayment = new Transaction()
      .withFromFundId(payment.getFromFundId())
      .withAmount(payment.getAmount())
      .withCurrency(payment.getCurrency())
      .withFiscalYearId(fY)
      .withSource(Transaction.Source.INVOICE)
      .withSourceInvoiceId(invoiceId)
      .withTransactionType(PENDING_PAYMENT)
      .withAwaitingPayment(new AwaitingPayment()
        .withReleaseEncumbrance(false)
        .withEncumbranceId(paymentEncumbranceId));


    JsonObject creditJsonTx = new JsonObject(getFile(CREDIT_SAMPLE));
    creditJsonTx.remove("id");

    Transaction credit = creditJsonTx.mapTo(Transaction.class);
    credit.setSourceInvoiceId(invoiceId);
    credit.setFiscalYearId(fY);
    credit.setToFundId(fromFundId);
    credit.setPaymentEncumbranceId(creditEncumbranceId);

    Transaction pendingPaymentForCredit = new Transaction()
      .withFromFundId(credit.getToFundId())
      .withAmount(-credit.getAmount())
      .withCurrency(credit.getCurrency())
      .withFiscalYearId(fY)
      .withSource(Transaction.Source.INVOICE)
      .withSourceInvoiceId(invoiceId)
      .withTransactionType(PENDING_PAYMENT)
      .withAwaitingPayment(new AwaitingPayment()
        .withReleaseEncumbrance(false)
        .withEncumbranceId(creditEncumbranceId));

    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(pendingPaymentForPayment).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201);
    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(pendingPaymentForCredit).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201);


    Transaction paymentEncumbranceBeforePayment= getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), paymentEncumbranceId,
            TRANSACTION_TENANT_HEADER).then()
            .statusCode(200)
            .extract()
            .as(Transaction.class);
    Transaction creditEncumbranceBeforePayment = getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), creditEncumbranceId,
            TRANSACTION_TENANT_HEADER).then()
            .statusCode(200)
            .extract()
            .as(Transaction.class);

    Budget budgetBefore = getBudgetAndValidate(budgetEndpointWithQueryParams);

    String paymentId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(payment)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    // payment does not appear in transaction table
    getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(404);

    String creditId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(400)
        .body(containsString(ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED));

    // 2 transactions(each for a payment and credit) appear in transaction table
    getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);
    getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), creditId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);

    Transaction paymentEncumbranceAfter = getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), paymentEncumbranceId,
        TRANSACTION_TENANT_HEADER).then()
          .statusCode(200)
          .extract()
          .as(Transaction.class);
    Transaction creditEncumbranceAfter = getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), creditEncumbranceId,
        TRANSACTION_TENANT_HEADER).then()
          .statusCode(200)
          .extract()
          .as(Transaction.class);

    // Check pending payments deleted
    TransactionCollection transactionCollection = getData(String.format("%s?query=sourceInvoiceId==%s AND transactionType==%s", TRANSACTION_ENDPOINT, invoiceId, PENDING_PAYMENT.value()), TRANSACTION_TENANT_HEADER)
      .then().statusCode(200)
      .extract()
      .as(TransactionCollection.class);

    assertEquals(0, transactionCollection.getTotalRecords());

    // Encumbrance Changes for payment
    assertEquals(payment.getAmount(), subtractValues(paymentEncumbranceAfter.getEncumbrance()
      .getAmountExpended(),
        paymentEncumbranceBeforePayment.getEncumbrance()
          .getAmountExpended()));

    assertEquals(payment.getAmount(), subtractValues(paymentEncumbranceBeforePayment.getEncumbrance()
                    .getAmountAwaitingPayment(),
            paymentEncumbranceAfter.getEncumbrance()
                    .getAmountAwaitingPayment()));

    // Encumbrance Changes for credit
    assertEquals(-credit.getAmount(), subtractValues(creditEncumbranceAfter.getEncumbrance()
      .getAmountExpended(),
        creditEncumbranceBeforePayment.getEncumbrance()
          .getAmountExpended()));

    assertEquals(-credit.getAmount(), subtractValues(creditEncumbranceBeforePayment.getEncumbrance()
                    .getAmountAwaitingPayment(),
            creditEncumbranceAfter.getEncumbrance()
                    .getAmountAwaitingPayment()));

    Budget budgetAfter = getBudgetAndValidate(budgetEndpointWithQueryParams);

    // awaiting payment must decreases by payment amount
    assertEquals(0d, subtractValues(budgetAfter.getAwaitingPayment(), budgetBefore.getAwaitingPayment()));

    // expenditures must increase by payment amt and decrease by credit amount
    assertEquals(subtractValues(payment.getAmount(), credit.getAmount()),
        subtractValues(budgetAfter.getExpenditures(), budgetBefore.getExpenditures()));

  }

  @Test
  void testCreatePaymentsCreditsAllOrNothingWithNoEncumbrances() {

    String invoiceId = UUID.randomUUID().toString();
    createInvoiceSummary(invoiceId, 3);

    JsonObject paymentJsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentJsonTx.remove("id");

    Transaction payment = paymentJsonTx.mapTo(Transaction.class);
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
    getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(404);

    JsonObject creditJsonTx = new JsonObject(getFile(CREDIT_SAMPLE));
    creditJsonTx.remove("id");

    Transaction credit = creditJsonTx.mapTo(Transaction.class);
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

    String creditId1 = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit.withSourceInvoiceLineId(UUID.randomUUID().toString()))
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class)
      .getId();

    // 3 transactions appear in transaction table
    getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);
    getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), creditId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);
    getDataById(ALLOCATION_TRANSACTION.getEndpointWithId(), creditId1, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);

    Budget paymentBudgetAfter = getBudgetAndValidate(paymentBudgetEndpointWithQueryParams);
    Budget creditBudgetAfter = getBudgetAndValidate(creditBudgetEndpointWithQueryParams);

    // payment changes  awaiting payment must decreases, expenditures increase
    assertEquals(paymentBudgetAfter.getAwaitingPayment(), subtractValues(paymentBudgetBefore.getAwaitingPayment(), payment.getAmount()));
    assertEquals(paymentBudgetAfter.getExpenditures(), sumValues(paymentBudgetBefore.getExpenditures(), payment.getAmount()));

    // available, unavailable, encumbrances  must remain the same
    assertEquals(paymentBudgetAfter.getAvailable(), paymentBudgetBefore.getAvailable());
    assertEquals(paymentBudgetAfter.getUnavailable(), paymentBudgetBefore.getUnavailable());
    assertEquals(paymentBudgetAfter.getEncumbered(), paymentBudgetBefore.getEncumbered());
    assertEquals(creditBudgetAfter.getAvailable(), creditBudgetBefore.getAvailable());
    assertEquals(creditBudgetAfter.getUnavailable(), creditBudgetBefore.getUnavailable());


    // credit changes  awaiting payment must increase, expenditures decreases
    assertEquals(creditBudgetAfter.getAwaitingPayment(), sumValues(creditBudgetBefore.getAwaitingPayment(), credit.getAmount(), credit.getAmount()));
    assertEquals(creditBudgetAfter.getExpenditures(), subtractValues(creditBudgetBefore.getExpenditures(), credit.getAmount(), credit.getAmount()));

  }

  @Test
  void testCreatePaymentWithoutInvoiceSummary() {

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
  void testPaymentsIdempotentInTemporaryTable() {

    JsonObject encumbranceJson = new JsonObject(getFile(TRANSACTION_SAMPLE));
    Transaction encumbrance = encumbranceJson.mapTo(Transaction.class);
    String encumbranceSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    createOrderSummary(encumbrance.getEncumbrance().getSourcePurchaseOrderId(), 1);

    postData(ALLOCATION_TRANSACTION.getEndpoint(), encumbranceSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Transaction.class);

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
  void testPaymentsWithInvalidPaymentEncumbranceInTemporaryTable() {

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

  @Test
  void testCreatePaymentWithRestrictedLedgerAndNotEnoughMoney() {

    String invoiceId = UUID.randomUUID().toString();
    createInvoiceSummary(invoiceId, 1);

    JsonObject paymentJsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentJsonTx.remove("id");

    Transaction payment = paymentJsonTx.mapTo(Transaction.class);
    payment.setSourceInvoiceId(invoiceId);
    payment.setPaymentEncumbranceId(null);
    payment.setAmount((double) Integer.MAX_VALUE);

    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(payment)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(FUND_CANNOT_BE_PAID));

  }

  @RepeatedTest(3)
  void testCreateAllOrNothing10Payments() {
    int numberOfPayments = 10;
    int initialNumberOfTransactions = getData(TRANSACTION_ENDPOINT, TRANSACTION_TENANT_HEADER).then()
      .extract()
      .as(TransactionCollection.class)
      .getTotalRecords();

    String invoiceId = UUID.randomUUID().toString();
    createInvoiceSummary(invoiceId, numberOfPayments);

    JsonObject paymentJsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentJsonTx.remove("id");

    Transaction payment = paymentJsonTx.mapTo(Transaction.class);
    payment.setSourceInvoiceId(invoiceId);
    payment.setPaymentEncumbranceId(null);
    payment.setAmount(1d);
    Stream.generate(() -> paymentJsonTx.mapTo(Transaction.class)
                            .withSourceInvoiceId(invoiceId)
                            .withPaymentEncumbranceId(null)
                            .withSourceInvoiceLineId(UUID.randomUUID().toString())
                            .withAmount(1d))
      .limit(numberOfPayments)
      .parallel()
      .forEach(transaction -> {
        postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(transaction)
          .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
          .statusCode(201);
      });

    int newNumberOfTransactions = getData(TRANSACTION_ENDPOINT, TRANSACTION_TENANT_HEADER).then()
      .extract()
      .as(TransactionCollection.class)
      .getTotalRecords();

    Assertions.assertEquals(initialNumberOfTransactions + numberOfPayments, newNumberOfTransactions,
      String.format("initialNum = %s, newNum = %s", initialNumberOfTransactions, newNumberOfTransactions));
  }

  protected void createOrderSummary(String orderId, int encumbranceNumber) {
    OrderTransactionSummary summary = new OrderTransactionSummary().withId(orderId).withNumTransactions(encumbranceNumber);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(summary)
      .encodePrettily(), TRANSACTION_TENANT_HEADER);
  }

  protected void createInvoiceSummary(String invoiceId, int numPaymentsCredits) {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary().withId(invoiceId).withNumPaymentsCredits(numPaymentsCredits).withNumPendingPayments(numPaymentsCredits);
    postData(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(summary)
      .encodePrettily(), TRANSACTION_TENANT_HEADER);
  }

  protected Budget getBudgetAndValidate(String endpoint) {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(BUDGETS, hasSize(1))
      .extract()
      .as(BudgetCollection.class).getBudgets().get(0);
  }

}
