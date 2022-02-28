package org.folio.rest.impl;

import static java.lang.Math.max;
import static org.folio.rest.impl.TransactionTest.TRANSACTION_TENANT_HEADER;
import static org.folio.rest.impl.TransactionsSummariesTest.INVOICE_TRANSACTION_SUMMARIES_ENDPOINT;
import static org.folio.rest.impl.TransactionsSummariesTest.ORDER_TRANSACTION_SUMMARIES_ENDPOINT;
import static org.folio.rest.impl.TransactionsSummariesTest.ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.PENDING;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.folio.service.budget.BudgetService.BUDGET_NOT_FOUND_FOR_TRANSACTION;
import static org.folio.service.transactions.AllOrNothingTransactionService.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;
import static org.folio.service.transactions.AllOrNothingTransactionService.BUDGET_IS_INACTIVE;
import static org.folio.service.transactions.restriction.BaseTransactionRestrictionService.FUND_CANNOT_BE_PAID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.util.UUID;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class EncumbrancesTest extends TestBase {

  public static final String ENCUMBRANCE_SAMPLE = "data/transactions/encumbrances/encumbrance_AFRICAHIST_306857_1.json";
  private static TenantJob tenantJob;

  @BeforeEach
  void prepareData() {
    tenantJob = prepareTenant(TRANSACTION_TENANT_HEADER, false, true);
  }

  @AfterEach
  void deleteData() {
    purge(TRANSACTION_TENANT_HEADER);
  }

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateEncumbranceAllOrNothingIdempotent() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget budgetBefore = buildBudget(fiscalYearId, fundId);
    String budgetId = postData(BUDGET.getEndpoint(), JsonObject.mapFrom(budgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class).getId();

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 2);
    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fundId);

    Transaction encumbrance1 = jsonTx.mapTo(Transaction.class);
    encumbrance1.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Transaction encumbrance2 = jsonTx.mapTo(Transaction.class);
    encumbrance2.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance2.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());


    // create 1st Encumbrance, expected number is 2
    String encumbrance1Id = postData(TRANSACTION.getEndpoint(), JsonObject.mapFrom(encumbrance1).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    // create 2nd Encumbrance
    String encumbrance2Id = postData(TRANSACTION.getEndpoint(), JsonObject.mapFrom(encumbrance2).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // 2 encumbrances appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    Budget budgetAfter = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);

    // check source budget and ledger totals
    final double amount = sumValues(encumbrance1.getAmount(), encumbrance2.getAmount());
    double expectedBudgetsAvailable;
    double expectedBudgetsUnavailable;
    double expectedBudgetsEncumbered;


    expectedBudgetsEncumbered = sumValues(budgetBefore.getEncumbered(), amount);
    expectedBudgetsAvailable = subtractValues(budgetBefore.getAvailable(), amount);
    expectedBudgetsUnavailable = sumValues(budgetBefore.getUnavailable(), amount);

    assertEquals(expectedBudgetsEncumbered, budgetAfter.getEncumbered());
    assertEquals(expectedBudgetsAvailable , budgetAfter.getAvailable());
    assertEquals(expectedBudgetsUnavailable, budgetAfter.getUnavailable());
    verifyBudgetTotalsAfter(budgetAfter);

    //create same encumbrances again
    postData(TRANSACTION.getEndpoint(), JsonObject.mapFrom(encumbrance1).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED));

    budgetAfter = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then().extract().as(Budget.class);

    // check source budget and ledger totals not changed
    assertEquals(expectedBudgetsEncumbered, budgetAfter.getEncumbered());
    assertEquals(expectedBudgetsAvailable , budgetAfter.getAvailable());
    assertEquals(expectedBudgetsUnavailable, budgetAfter.getUnavailable());
    verifyBudgetTotalsAfter(budgetAfter);

  }

  @Test
  void testCreateEncumbranceWithNotEnoughBudgetMoney() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId);
    postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 1);
    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fundId);

    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.setAmount(1000000d);
    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    // create Encumbrance
    postData(TRANSACTION.getEndpoint(), JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(FUND_CANNOT_BE_PAID));

  }

  @Test
  void testCreateEncumbranceFromInactiveBudget() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId).withBudgetStatus(Budget.BudgetStatus.INACTIVE);
    postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 1);
    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fundId);

    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.setAmount((double) Integer.MAX_VALUE);
    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    // create Encumbrance
    postData(TRANSACTION.getEndpoint(), JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(BUDGET_IS_INACTIVE));

  }

  @Test
  void testCreateEncumbranceWithoutSummary() throws MalformedURLException {

    String orderId = UUID.randomUUID().toString();

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400);

  }

  @Test
  void testCreateEncumbranceWithoutBudget() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 2);
    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fundId);

    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.setFiscalYearId(UUID.randomUUID().toString());

    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400).body(containsString(BUDGET_NOT_FOUND_FOR_TRANSACTION));

  }

  protected void createOrderSummary(String orderId, int encumbranceNumber) throws MalformedURLException {
    OrderTransactionSummary summary = new OrderTransactionSummary().withId(orderId).withNumTransactions(encumbranceNumber);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(summary)
      .encodePrettily(), TRANSACTION_TENANT_HEADER);
  }

  protected void updateOrderSummary(String orderId, int encumbranceNumber) throws MalformedURLException {
    OrderTransactionSummary summary = getDataById(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, orderId, TRANSACTION_TENANT_HEADER)
      .as(OrderTransactionSummary.class);
    summary.setNumTransactions(encumbranceNumber);
    putData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, orderId, JsonObject.mapFrom(summary).encodePrettily(), TRANSACTION_TENANT_HEADER)
      .then()
        .statusCode(204);

  }

  protected void createInvoiceSummary(String invoiceId, int numEncumbrances) throws MalformedURLException {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary().withId(invoiceId).withNumPaymentsCredits(numEncumbrances).withNumPendingPayments(numEncumbrances);
    postData(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(summary)
      .encodePrettily(), TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateEncumbranceWithMissedRequiredFields() throws MalformedURLException {

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));

    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.setEncumbrance(null);
    encumbrance.setFromFundId(null);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    Errors errors = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(422).extract().as(Errors.class);
    assertThat(errors.getErrors(), hasSize(2));

  }

  @Test
  void testCreateEncumbrancesDuplicateInTemporaryTable() throws MalformedURLException {
    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId);

    postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class);

    String orderId = UUID.randomUUID().toString();

    createOrderSummary(orderId, 2);
    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fundId);
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    String encumbranceId = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbranceId, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    // create encumbrance again
    postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201);

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbranceId, TRANSACTION_TENANT_HEADER).then().statusCode(404);

  }

  @Test
  void testCreateEncumbrancesDuplicateInTransactionTable() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId);

    postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId,  2);

    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fundId);
    Transaction encumbrance1 = jsonTx.mapTo(Transaction.class);
    encumbrance1.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Transaction encumbrance2 = jsonTx.mapTo(Transaction.class);
    encumbrance2.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance2.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());

    String transactionSample1 = JsonObject.mapFrom(encumbrance1).encodePrettily();

    // create 1st Encumbrance, expected number is 2
    String encumbrance1Id = postData(TRANSACTION.getEndpoint(), transactionSample1, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    String transactionSample2 = JsonObject.mapFrom(encumbrance2).encodePrettily();

    // create 2nd Encumbrance
    String encumbrance2Id = postData(TRANSACTION.getEndpoint(), transactionSample2, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // 2 encumbrances appear in transaction table, temp transactions deleted
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    postData(TRANSACTION.getEndpoint(), transactionSample1, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED));

  }

  @Test
  void testUpdateEncumbranceAllOrNothing() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId);

    String budgetId = postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class).getId();

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 3);

    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fundId);
    Transaction encumbrance1 = jsonTx.mapTo(Transaction.class);
    encumbrance1.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Transaction encumbrance2 = jsonTx.mapTo(Transaction.class);
    encumbrance2.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance2.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());
    encumbrance2.setAmount(0d);
    encumbrance2.getEncumbrance().setInitialAmountEncumbered(0d);
    encumbrance2.getEncumbrance().setStatus(PENDING);

    Transaction encumbrance3 = jsonTx.mapTo(Transaction.class);
    encumbrance3.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance3.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());

    String transactionSample = JsonObject.mapFrom(encumbrance1).encodePrettily();


    // create 1st Encumbrance, expected number is 2
    String encumbrance1Id = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    transactionSample = JsonObject.mapFrom(encumbrance2).encodePrettily();

    // create 2nd Encumbrance
    String encumbrance2Id = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // create 3rd Encumbrance
    transactionSample = JsonObject.mapFrom(encumbrance3).encodePrettily();
    String encumbrance3Id = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED));

    // 3 encumbrances appear in transaction table
     getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
     getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
     getDataById(TRANSACTION.getEndpointWithId(), encumbrance3Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    Budget fromBudgetBeforeUpdate = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);

    verifyBudgetTotalsAfter(fromBudgetBeforeUpdate);
    double releasedAmount = encumbrance1.getAmount();

    encumbrance1.getEncumbrance().setStatus(Encumbrance.Status.RELEASED);
    encumbrance2.setAmount(200d);
    encumbrance2.getEncumbrance().setInitialAmountEncumbered(200d);

    encumbrance2.getEncumbrance().setStatus(Encumbrance.Status.UNRELEASED);

    updateOrderSummary(orderId, 3);
    // First encumbrance update, save to temp table, changes won't get to transaction table
    putData(TRANSACTION.getEndpointWithId(), encumbrance1Id, JsonObject.mapFrom(encumbrance1).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);
    Transaction transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(Encumbrance.Status.UNRELEASED, transaction1FromStorage.getEncumbrance().getStatus());
    assertEquals(transaction1FromStorage.getAmount(), releasedAmount);

    // Second encumbrance update, changes won't get to transaction table
    putData(TRANSACTION.getEndpointWithId(), encumbrance2Id, JsonObject.mapFrom(encumbrance2).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);

    // Third encumbrance update with pending status, changes for three encumbrances will get to transaction table
    encumbrance3.getEncumbrance().setStatus(PENDING);
    putData(TRANSACTION.getEndpointWithId(), encumbrance3Id, JsonObject.mapFrom(encumbrance3).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);


    // Check all-or-nothing results
    // 1st transaction
    transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(Encumbrance.Status.RELEASED, transaction1FromStorage.getEncumbrance().getStatus());
    assertEquals(0d, transaction1FromStorage.getAmount());

    // 2nd transaction
    Transaction transaction2FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(transaction2FromStorage.getEncumbrance().getAmountAwaitingPayment(), encumbrance2.getEncumbrance().getAmountAwaitingPayment());

    double expectedAmount2 = subtractValues(encumbrance2.getAmount(), encumbrance2.getEncumbrance().getAmountAwaitingPayment(), encumbrance2.getEncumbrance().getAmountExpended());
    assertEquals(expectedAmount2, transaction2FromStorage.getAmount());

    // 3rd transaction
    Transaction transaction3FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance3Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(0, transaction3FromStorage.getAmount());
    assertEquals(0, transaction3FromStorage.getEncumbrance().getInitialAmountEncumbered());


    // check budget updates
    Budget fromBudgetAfterUpdate = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);

    double transactionsTotalAmount = sumValues(encumbrance1.getAmount(), encumbrance3.getAmount(), -transaction2FromStorage.getAmount());
    double expectedBudgetsEncumbered = subtractValues(fromBudgetBeforeUpdate.getEncumbered(), transactionsTotalAmount);

    double expectedBudgetsAvailable = sumValues(fromBudgetBeforeUpdate.getAvailable(), transactionsTotalAmount);
    double expectedBudgetsUnavailable = subtractValues(fromBudgetBeforeUpdate.getUnavailable(), transactionsTotalAmount);
    expectedBudgetsUnavailable = expectedBudgetsUnavailable < 0 ? 0 : expectedBudgetsUnavailable;

    assertEquals(expectedBudgetsEncumbered, fromBudgetAfterUpdate.getEncumbered());
    assertEquals(expectedBudgetsAvailable , fromBudgetAfterUpdate.getAvailable());
    assertEquals(expectedBudgetsUnavailable, fromBudgetAfterUpdate.getUnavailable());

    verifyBudgetTotalsAfter(fromBudgetAfterUpdate);

  }

  @Test
  void testUpdateAlreadyReleasedEncumbranceBudgetNotUpdated() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId);

    String budgetId = postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class).getId();

    String orderId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 1);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance.getEncumbrance().setStatus(Encumbrance.Status.RELEASED);
    encumbrance.setAmount(0d);
    encumbrance.getEncumbrance().setAmountAwaitingPayment(10d);
    encumbrance.setSourceFiscalYearId(fiscalYearId);
    encumbrance.setFiscalYearId(fiscalYearId);
    encumbrance.setFromFundId(fundId);
    encumbrance.setSourceInvoiceId(invoiceId);


    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    // create 1st Encumbrance, expected number is 1
    String encumbrance1Id = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance appears in transaction table
    encumbrance = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);


    updateOrderSummary(orderId, 1);

    encumbrance.getEncumbrance().setAmountAwaitingPayment(5d);
    encumbrance.setAmount(2d);

    putData(TRANSACTION.getEndpointWithId(), encumbrance1Id, JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);
    Transaction transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(10d, transaction1FromStorage.getEncumbrance().getAmountAwaitingPayment());
    assertEquals(0d, transaction1FromStorage.getAmount());
    Budget fromBudgetAfterUpdate = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);

    checkBudgetTotalsNotChanged(fromBudgetBefore, fromBudgetAfterUpdate);

  }

  @Test
  void testUpdateReleasedToUnreleased() throws MalformedURLException {
    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId);
    String fromBudgetBeforeSerialized = JsonObject.mapFrom(fromBudgetBefore).encodePrettily();
    String budgetId = postData(BUDGET.getEndpoint(), fromBudgetBeforeSerialized, TRANSACTION_TENANT_HEADER)
      .then().statusCode(201).extract().as(Budget.class).getId();

    String orderId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 1);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.withAmount(0d)
      .withSourceFiscalYearId(fiscalYearId)
      .withFiscalYearId(fiscalYearId)
      .withFromFundId(fundId)
      .withSourceInvoiceId(invoiceId);
    encumbrance.getEncumbrance()
      .withSourcePurchaseOrderId(orderId)
      .withStatus(Encumbrance.Status.RELEASED)
      .withAmountExpended(10d)
      .withAmountAwaitingPayment(5d)
      .withInitialAmountEncumbered(100d);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    // create 1st encumbrance, expected number is 1
    String encumbrance1Id = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER)
      .then().statusCode(201).extract().as(Transaction.class).getId();

    // encumbrance appears in transaction table
    String transactionById = TRANSACTION.getEndpointWithId();
    encumbrance = getDataById(transactionById, encumbrance1Id, TRANSACTION_TENANT_HEADER)
      .then().statusCode(200).extract().as(Transaction.class);
    assertEquals(0d, encumbrance.getAmount());
    assertEquals(100d, encumbrance.getEncumbrance().getInitialAmountEncumbered());

    // unrelease the encumbrance
    encumbrance.getEncumbrance().setStatus(Encumbrance.Status.UNRELEASED);
    updateOrderSummary(orderId, 1);
    String encumbranceSerialized = JsonObject.mapFrom(encumbrance).encodePrettily();
    putData(transactionById, encumbrance1Id, encumbranceSerialized, TRANSACTION_TENANT_HEADER)
      .then().statusCode(204);
    Transaction transaction1FromStorage = getDataById(transactionById, encumbrance1Id, TRANSACTION_TENANT_HEADER)
      .then().statusCode(200).extract().as(Transaction.class);
    assertEquals(Encumbrance.Status.UNRELEASED, transaction1FromStorage.getEncumbrance().getStatus());
    assertEquals(85d, transaction1FromStorage.getAmount());

    // check the budget
    Budget fromBudgetAfterUpdate = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER)
      .then().statusCode(200).extract().as(Budget.class);
    assertEquals(sumValues(fromBudgetBefore.getEncumbered(), 85d), fromBudgetAfterUpdate.getEncumbered());
    assertEquals(subtractValues(fromBudgetBefore.getAvailable(), 85d), fromBudgetAfterUpdate.getAvailable());
    assertEquals(sumValues(fromBudgetBefore.getUnavailable(), 85d), fromBudgetAfterUpdate.getUnavailable());
    assertEquals(fromBudgetBefore.getAwaitingPayment(), fromBudgetAfterUpdate.getAwaitingPayment());
  }

  @Test
  void testUpdateEncumbranceWithoutStatusChangeBudgetUpdated() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget fromBudgetBefore = buildBudget(fiscalYearId, fundId);

    String budgetId = postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class).getId();

    String orderId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 1);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance.getEncumbrance().setStatus(Encumbrance.Status.UNRELEASED);
    encumbrance.setAmount(10d);
    encumbrance.getEncumbrance().setAmountAwaitingPayment(10d);
    encumbrance.getEncumbrance().setInitialAmountEncumbered(20d);
    encumbrance.setSourceFiscalYearId(fiscalYearId);
    encumbrance.setFiscalYearId(fiscalYearId);
    encumbrance.setFromFundId(fundId);
    encumbrance.setSourceInvoiceId(invoiceId);


    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    // create 1st Encumbrance, expected number is 1
    String encumbrance1Id = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance appears in transaction table
    Budget fromBudgetBeforeUpdate = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);
    encumbrance = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);


    updateOrderSummary(orderId, 1);

    encumbrance.setAmount(30d);

    putData(TRANSACTION.getEndpointWithId(), encumbrance1Id, JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);

    Budget fromBudgetAfterUpdate = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);

    assertEquals(subtractValues(fromBudgetBeforeUpdate.getAvailable(), 20d), fromBudgetAfterUpdate.getAvailable());
    assertEquals(sumValues(fromBudgetBeforeUpdate.getUnavailable(), 20d), fromBudgetAfterUpdate.getUnavailable());
    assertEquals(sumValues(fromBudgetBeforeUpdate.getEncumbered(), 20d), fromBudgetAfterUpdate.getEncumbered());

  }

  @Test
  void testUpdateEncumbranceNotFound() throws MalformedURLException {

    String invoiceId = UUID.randomUUID().toString();
    createInvoiceSummary(invoiceId, 2);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.setSourceInvoiceId(invoiceId);

    // Try to update non-existent transaction
    putData(TRANSACTION.getEndpointWithId(), UUID.randomUUID().toString(), JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(404);

  }

  @Test
  void tesPostEncumbranceUnavailableMustNotIncludeOverEncumberedAmounts() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fundId = createFund(ledgerId);

    Budget budgetBefore = buildBudget(fiscalYearId, fundId);

    String budgetId = postData(BUDGET.getEndpoint(), JsonObject.mapFrom(budgetBefore).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class).getId();

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 1);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance.setFromFundId(fundId);
    encumbrance.setFiscalYearId(fiscalYearId);
    encumbrance.setSourceFiscalYearId(null);

    final double transactionAmount = 10000d;
    encumbrance.setAmount(transactionAmount);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    String encumbranceId = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbranceId, TRANSACTION_TENANT_HEADER).then().statusCode(200);

    Budget budgetAfter = getDataById(BUDGET.getEndpointWithId(), budgetId, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200).extract().as(Budget.class);

    assertEquals(sumValues(budgetBefore.getEncumbered(), transactionAmount), budgetAfter.getEncumbered());
    verifyBudgetTotalsAfter(budgetAfter);

  }

  @Test
  void testUpdateAndCreateEncumbrancesAllOrNothing() throws MalformedURLException {

    String fiscalYearId = createFiscalYear();
    String ledgerId = createLedger(fiscalYearId, true);
    String fund1Id = createFund(ledgerId);

    Fund fund2 = new JsonObject(getFile(FUND.getPathToSampleFile())).mapTo(Fund.class).withId(null).withLedgerId(ledgerId).withCode("test2");
    String fund2Id = postData(FUND.getEndpoint(), JsonObject.mapFrom(fund2).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Fund.class).getId();

    Budget fromBudget1Before = buildBudget(fiscalYearId, fund1Id);
    Budget fromBudget2Before = buildBudget(fiscalYearId, fund2Id).withName("test2");

    String budget1Id = postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudget1Before).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class).getId();
    String budget2Id = postData(BUDGET.getEndpoint(), JsonObject.mapFrom(fromBudget2Before).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Budget.class).getId();

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 1);

    JsonObject jsonTx = prepareEncumbrance(fiscalYearId, fund1Id);
    Transaction encumbrance1 = jsonTx.mapTo(Transaction.class);
    encumbrance1.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Transaction encumbrance2 = jsonTx.mapTo(Transaction.class);
    encumbrance2.setFromFundId(fund2Id);
    encumbrance2.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance2.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());
    encumbrance2.setAmount(encumbrance2.getEncumbrance().getInitialAmountEncumbered());
    encumbrance2.getEncumbrance().setAmountAwaitingPayment(0d);
    encumbrance2.getEncumbrance().setAmountExpended(0d);

    String transactionSample = JsonObject.mapFrom(encumbrance1).encodePrettily();

    // create 1st Encumbrance, expected number is 1
    String encumbrance1Id = postData(TRANSACTION.getEndpoint(), transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance appears in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    Budget fromBudget1BeforeUpdate = getDataById(BUDGET.getEndpointWithId(), budget1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);

    verifyBudgetTotalsAfter(fromBudget1BeforeUpdate);
    double releasedAmount = encumbrance1.getAmount();

    encumbrance1.getEncumbrance().setStatus(Encumbrance.Status.RELEASED);

    updateOrderSummary(orderId, 2);
    // First encumbrance update, save to temp table, changes won't get to transaction table
    putData(TRANSACTION.getEndpointWithId(), encumbrance1Id, JsonObject.mapFrom(encumbrance1).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);
    Transaction transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(Encumbrance.Status.UNRELEASED, transaction1FromStorage.getEncumbrance().getStatus());
    assertEquals(transaction1FromStorage.getAmount(), releasedAmount);

    // Second encumbrance creation, changes won't get to transaction table
    String encumbrance2Id = postData(TRANSACTION.getEndpoint(), JsonObject.mapFrom(encumbrance2).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // Check all-or-nothing results
    // 1st transaction
    transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(Encumbrance.Status.RELEASED, transaction1FromStorage.getEncumbrance().getStatus());
    assertEquals(0d, transaction1FromStorage.getAmount());

    // 2nd transaction
    Transaction transaction2FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(transaction2FromStorage.getEncumbrance().getAmountAwaitingPayment(), encumbrance2.getEncumbrance().getAmountAwaitingPayment());

    double expectedAmount2 = subtractValues(encumbrance2.getAmount(), encumbrance2.getEncumbrance().getAmountAwaitingPayment(), encumbrance2.getEncumbrance().getAmountExpended());
    assertEquals(expectedAmount2, transaction2FromStorage.getAmount());

    // check budget updates
    Budget fromBudget1AfterUpdate = getDataById(BUDGET.getEndpointWithId(), budget1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);


    double expectedBudget1Encumbered = subtractValues(fromBudget1BeforeUpdate.getEncumbered(), releasedAmount);
    double expectedBudget1Available = sumValues(fromBudget1BeforeUpdate.getAvailable(), releasedAmount);
    double expectedBudget1Unavailable = subtractValues(fromBudget1BeforeUpdate.getUnavailable(), releasedAmount);
    expectedBudget1Unavailable = expectedBudget1Unavailable < 0 ? 0 : expectedBudget1Unavailable;

    assertEquals(expectedBudget1Encumbered, fromBudget1AfterUpdate.getEncumbered());
    assertEquals(expectedBudget1Available , fromBudget1AfterUpdate.getAvailable());
    assertEquals(expectedBudget1Unavailable, fromBudget1AfterUpdate.getUnavailable());

    Budget fromBudget2AfterUpdate = getDataById(BUDGET.getEndpointWithId(), budget2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Budget.class);


    double expectedBudget2Encumbered = sumValues(fromBudget2Before.getEncumbered(), transaction2FromStorage.getAmount());
    double expectedBudget2Available = subtractValues(fromBudget2Before.getAvailable(), transaction2FromStorage.getAmount());
    double expectedBudget2Unavailable = sumValues(fromBudget2Before.getUnavailable(), transaction2FromStorage.getAmount());
    expectedBudget2Unavailable = expectedBudget2Unavailable < 0 ? 0 : expectedBudget2Unavailable;

    assertEquals(expectedBudget2Encumbered, fromBudget2AfterUpdate.getEncumbered());
    assertEquals(expectedBudget2Available , fromBudget2AfterUpdate.getAvailable());
    assertEquals(expectedBudget2Unavailable, fromBudget2AfterUpdate.getUnavailable());

    verifyBudgetTotalsAfter(fromBudget1AfterUpdate);
  }


  private void verifyBudgetTotalsAfter(Budget budget) {
    assertTrue(budget.getUnavailable() >= 0);
    double expectedUnavailable = sumValues(budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures());
    assertEquals(expectedUnavailable, budget.getUnavailable());
    assertEquals(subtractValues(sumValues(budget.getInitialAllocation(), budget.getAllocationTo()), budget.getAllocationFrom()), budget.getAllocated());
  }


  private String createFund(String ledgerId) throws MalformedURLException {
    Fund fund = new JsonObject(getFile(FUND.getPathToSampleFile())).mapTo(Fund.class).withId(null).withLedgerId(ledgerId);
    return postData(FUND.getEndpoint(), JsonObject.mapFrom(fund).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Fund.class).getId();
  }

  private String createFiscalYear() throws MalformedURLException {
    FiscalYear fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class).withId(null);
    return postData(FISCAL_YEAR.getEndpoint(), JsonObject.mapFrom(fiscalYear).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(FiscalYear.class).getId();
  }

  private String createLedger(String fiscalYearId, boolean restrictEncumbrance) throws MalformedURLException {
    Ledger ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class)
      .withId(null)
      .withFiscalYearOneId(fiscalYearId)
      .withRestrictEncumbrance(restrictEncumbrance);
    return postData(LEDGER.getEndpoint(), JsonObject.mapFrom(ledger).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Ledger.class).getId();
  }

  private Budget buildBudget(String fiscalYearId, String fundId) {
    final double allocated = 10000d;
    final double available = 7000d;
    final double unavailable = 3000d;
    final double overEncumbrance = 150d;
    return new JsonObject(getFile(BUDGET.getPathToSampleFile())).mapTo(Budget.class)
      .withId(null)
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withInitialAllocation(allocated)
      .withAvailable(available)
      .withExpenditures(1500d)
      .withAwaitingPayment(1500d)
      .withUnavailable(unavailable)
      .withAllowableEncumbrance(overEncumbrance)
      .withEncumbered(0d)
      .withOverEncumbrance(0d);
  }

  private JsonObject prepareEncumbrance(String fiscalYearId, String fundId) {
    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    jsonTx.put("fiscalYearId", fiscalYearId);
    jsonTx.put("fromFundId", fundId);
    jsonTx.remove("sourceFiscalYearId");
    return jsonTx;
  }

  private void checkBudgetTotalsNotChanged(Budget fromBudgetBefore, Budget fromBudgetAfterUpdate) {
    assertEquals(fromBudgetBefore.getEncumbered(), fromBudgetAfterUpdate.getEncumbered());
    assertEquals(fromBudgetBefore.getAvailable() , fromBudgetAfterUpdate.getAvailable());
    assertEquals(fromBudgetBefore.getUnavailable(), fromBudgetAfterUpdate.getUnavailable());
    assertEquals(fromBudgetBefore.getAwaitingPayment(), fromBudgetAfterUpdate.getAwaitingPayment());
  }

}
