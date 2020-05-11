package org.folio.rest.impl;

import static java.lang.Math.max;
import static org.folio.rest.impl.EncumbrancesTest.ENCUMBRANCE_SAMPLE;
import static org.folio.rest.impl.TransactionTest.BUDGETS;
import static org.folio.rest.impl.TransactionTest.BUDGETS_QUERY;
import static org.folio.rest.impl.TransactionTest.LEDGER_FYS_ENDPOINT;
import static org.folio.rest.impl.TransactionTest.TRANSACTION_ENDPOINT;
import static org.folio.rest.impl.TransactionTest.TRANSACTION_TENANT_HEADER;
import static org.folio.rest.impl.TransactionsSummariesTest.INVOICE_TRANSACTION_SUMMARIES_ENDPOINT;
import static org.folio.rest.impl.TransactionsSummariesTest.ORDER_TRANSACTION_SUMMARIES_ENDPOINT;
import static org.folio.rest.transaction.AllOrNothingHandler.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;
import static org.folio.rest.transaction.AllOrNothingHandler.FUND_CANNOT_BE_PAID;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.stream.IntStream;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.LedgerFYCollection;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class PaymentsCreditsTest extends TestBase {
  private static final String CREDIT_SAMPLE = "data/transactions/credits/credit_CANHIST_30121.json";
  private static final String PAYMENT_SAMPLE = "data/transactions/payments/payment_ENDOW-SUBN_30121.json";
  private static final String TRANSACTION_SAMPLE = "data/transactions/encumbrances/encumbrance_ENDOW-SUBN_S60402_80.json";

  @BeforeEach
  void prepareData() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
  }

  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreatePaymentsCreditsAllOrNothing() throws MalformedURLException {

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
    Fund paymentFund = getDataById(FUND.getEndpointWithId(), fromFundId, TRANSACTION_TENANT_HEADER).as(Fund.class);
    String paymentLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, paymentFund.getLedgerId(), fY);

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
    LedgerFY ledgerFYBefore = getLedgerFYAndValidate(paymentLedgerFYEndpointWithQueryParams);

    JsonObject paymentJsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentJsonTx.remove("id");

    Transaction payment = paymentJsonTx.mapTo(Transaction.class);
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

    JsonObject creditJsonTx = new JsonObject(getFile(CREDIT_SAMPLE));
    creditJsonTx.remove("id");

    Transaction credit = creditJsonTx.mapTo(Transaction.class);
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

    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(400)
        .body(containsString(ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED));

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

    LedgerFY ledgerFYAfter = getLedgerFYAndValidate(paymentLedgerFYEndpointWithQueryParams);

    assertEquals(ledgerFYBefore, ledgerFYAfter);

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
    assertEquals(0d, subtractValues(budgetAfter.getAwaitingPayment(), budgetBefore.getAwaitingPayment()));
    // encumbered must increase by credit amount
    assertEquals(credit.getAmount(), subtractValues(budgetAfter.getEncumbered(), budgetBefore.getEncumbered()));
    // expenditures must increase by payment amt and decrease by credit amount
    assertEquals(subtractValues(payment.getAmount(), credit.getAmount()),
        subtractValues(budgetAfter.getExpenditures(), budgetBefore.getExpenditures()));

  }

  @Test
  void testCreatePaymentsCreditsAllOrNothingWithNoEncumbrances() throws MalformedURLException {

    String invoiceId = UUID.randomUUID().toString();
    createInvoiceSummary(invoiceId, 3);

    JsonObject paymentJsonTx = new JsonObject(getFile(PAYMENT_SAMPLE));
    paymentJsonTx.remove("id");

    Transaction payment = paymentJsonTx.mapTo(Transaction.class);
    payment.setSourceInvoiceId(invoiceId);
    payment.setPaymentEncumbranceId(null);

    String fY = payment.getFiscalYearId();
    String fromFundId = payment.getFromFundId();


    Fund paymentFund = getDataById(FUND.getEndpointWithId(), fromFundId, TRANSACTION_TENANT_HEADER).as(Fund.class);

    // prepare budget queries
    String paymentBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String paymentLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, paymentFund.getLedgerId(), fY);

    Budget paymentBudgetBefore = getBudgetAndValidate(paymentBudgetEndpointWithQueryParams);
    LedgerFY paymentLedgerFYBefore = getLedgerFYAndValidate(paymentLedgerFYEndpointWithQueryParams);

    String paymentId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(payment)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    // payment does not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(404);

    JsonObject creditJsonTx = new JsonObject(getFile(CREDIT_SAMPLE));
    creditJsonTx.remove("id");

    Transaction credit = creditJsonTx.mapTo(Transaction.class);
    credit.setSourceInvoiceId(invoiceId);
    credit.setFiscalYearId(fY);
    credit.setPaymentEncumbranceId(null);
    credit.setAmount(30d);

    Fund creditFund = getDataById(FUND.getEndpointWithId(), credit.getToFundId(), TRANSACTION_TENANT_HEADER).as(Fund.class);

    String creditBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, credit.getToFundId());
    String creditLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, creditFund.getLedgerId(), credit.getFiscalYearId());

    LedgerFY creditLedgerFYBefore = getLedgerFYAndValidate(creditLedgerFYEndpointWithQueryParams);
    Budget creditBudgetBefore = getBudgetAndValidate(creditBudgetEndpointWithQueryParams);

    String creditId = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
        .statusCode(201)
        .extract()
        .as(Transaction.class)
        .getId();

    String creditId1 = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(credit)
      .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class)
      .getId();

    // 3 transactions appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), paymentId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), creditId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), creditId1, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .as(Transaction.class);

    Budget paymentBudgetAfter = getBudgetAndValidate(paymentBudgetEndpointWithQueryParams);
    Budget creditBudgetAfter = getBudgetAndValidate(creditBudgetEndpointWithQueryParams);

    LedgerFY creditLedgerFYAfter = getLedgerFYAndValidate(creditLedgerFYEndpointWithQueryParams);
    LedgerFY paymentLedgerFYAfter = getLedgerFYAndValidate(paymentLedgerFYEndpointWithQueryParams);

    // awaiting payment, encumbered must remain the same
    assertEquals(0d, subtractValues(paymentBudgetAfter.getAwaitingPayment(), paymentBudgetBefore.getAwaitingPayment()));
    assertEquals(0d, subtractValues(paymentBudgetAfter.getEncumbered(), paymentBudgetBefore.getEncumbered()));
    assertEquals(payment.getAmount(), subtractValues(paymentBudgetAfter.getExpenditures(), paymentBudgetBefore.getExpenditures()));

    // payment changes, available must decrease and unavailable increases
    assertEquals(-payment.getAmount(), subtractValues(paymentBudgetAfter.getAvailable(), paymentBudgetBefore.getAvailable()));
    assertEquals(payment.getAmount(), subtractValues(paymentBudgetAfter.getUnavailable(), paymentBudgetBefore.getUnavailable()));

    Double expectedBudgetAvailable = sumValues(sumValues(creditBudgetBefore.getAvailable(), credit.getAmount()), credit.getAmount());
    Double expectedBudgetUnavailable = subtractValues(subtractValues(creditBudgetBefore.getUnavailable(), credit.getAmount()), credit.getAmount());
    // credit changes, available must increase and unavailable decreases
    assertEquals(expectedBudgetAvailable, creditBudgetAfter.getAvailable());
    assertEquals(expectedBudgetUnavailable, creditBudgetAfter.getUnavailable());
    assertEquals(max(0d, -credit.getAmount()), subtractValues(creditBudgetAfter.getExpenditures(), creditBudgetBefore.getExpenditures()));

    assertEquals(paymentLedgerFYAfter.getAvailable(), subtractValues(paymentLedgerFYBefore.getAvailable(), payment.getAmount()));
    assertEquals(paymentLedgerFYAfter.getUnavailable(), sumValues(paymentLedgerFYBefore.getUnavailable(), payment.getAmount()));

    assertEquals(creditLedgerFYAfter.getAvailable(),  sumValues(sumValues(creditLedgerFYBefore.getAvailable(), credit.getAmount()), credit.getAmount()));
    assertEquals(creditLedgerFYAfter.getUnavailable(), max(subtractValues(subtractValues(creditLedgerFYBefore.getUnavailable(), credit.getAmount()), credit.getAmount()), 0));

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

    JsonObject encumbranceJson = new JsonObject(getFile(TRANSACTION_SAMPLE));
    Transaction encumbrance = encumbranceJson.mapTo(Transaction.class);
    String encumbranceSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    createOrderSummary(encumbrance.getEncumbrance().getSourcePurchaseOrderId(), 1);

    postData(TRANSACTION.getEndpoint(), encumbranceSample, TRANSACTION_TENANT_HEADER).then()
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

  @Test
  void testCreatePaymentWithRestrictedLedgerAndNotEnoughMoney() throws MalformedURLException {

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
  void testCreateAllOrNothing10Payments() throws MalformedURLException {
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

    IntStream.range(0, numberOfPayments)
      .parallel()
      .forEach(i -> {
        try {
          postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(payment)
            .encodePrettily(), TRANSACTION_TENANT_HEADER).then()
              .statusCode(201);
        } catch (MalformedURLException e) {
          logger.error(e.getMessage());
        }
      });

    int newNumberOfTransactions = getData(TRANSACTION_ENDPOINT, TRANSACTION_TENANT_HEADER).then()
      .extract()
      .as(TransactionCollection.class)
      .getTotalRecords();

    Assertions.assertEquals(initialNumberOfTransactions + numberOfPayments, newNumberOfTransactions,
      String.format("initialNum = %s, newNum = %s", initialNumberOfTransactions, newNumberOfTransactions));
  }

  protected void createOrderSummary(String orderId, int encumbranceNumber) throws MalformedURLException {
    OrderTransactionSummary summary = new OrderTransactionSummary().withId(orderId).withNumTransactions(encumbranceNumber);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(summary)
      .encodePrettily(), TRANSACTION_TENANT_HEADER);
  }

  protected void createInvoiceSummary(String invoiceId, int numPaymentsCredits) throws MalformedURLException {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary().withId(invoiceId).withNumPaymentsCredits(numPaymentsCredits).withNumEncumbrances(0);
    postData(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(summary)
      .encodePrettily(), TRANSACTION_TENANT_HEADER);
  }

  protected Budget getBudgetAndValidate(String endpoint) throws MalformedURLException {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(BUDGETS, hasSize(1))
      .extract()
      .as(BudgetCollection.class).getBudgets().get(0);
  }

  protected LedgerFY getLedgerFYAndValidate(String endpoint) throws MalformedURLException {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body("ledgerFY", hasSize(1))
      .extract()
      .as(LedgerFYCollection.class).getLedgerFY().get(0);
  }

}
