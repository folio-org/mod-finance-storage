package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.impl.TransactionsSummariesTest.ORDER_TRANSACTION_SUMMARIES_ENDPOINT;
import static org.folio.rest.persist.HelperUtils.getEndpoint;
import static org.folio.rest.transaction.AllOrNothingHandler.BUDGET_NOT_FOUND_FOR_TRANSACTION;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.LedgerFYCollection;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerFiscalYears;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

class TransactionTest extends TestBase {
  private static final String TRANSACTION_ENDPOINT = TRANSACTION.getEndpoint();
  private static final String TRANSACTION_TEST_TENANT = "transaction_test_tenant";
  private static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  private static final String FY_FUND_QUERY = "?query=fiscalYearId==%s AND fundId==%s";
  private static final String LEDGER_QUERY = "?query=fund.id==%s";
  private static final String LEDGER_FY_QUERY = "?query=ledgerId==%s AND fiscalYearId==%s";
  public static final String ALLOCATION_SAMPLE = "data/transactions/allocation_AFRICAHIST-FY20_ANZHIST-FY20.json";
  public static final String ENCUMBRANCE_SAMPLE = "data/transactions/encumbrance_AFRICAHIST_306857_1.json";
  private static String BUDGETS_QUERY = BUDGET.getEndpoint() + FY_FUND_QUERY;
  private static String LEDGERS_QUERY = LEDGER.getEndpoint() + LEDGER_QUERY;
  private static String LEDGER_FYS_ENDPOINT = getEndpoint(FinanceStorageLedgerFiscalYears.class) + LEDGER_FY_QUERY;
  private static final String BUDGETS = "budgets";
  private static final String LEDGERS = "ledgers";

  @Test
  void testCreateAllocation() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("id");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");
    String toFundId = jsonTx.getString("toFundId");

    // prepare budget/ledger queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String fromLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, fromFundId);
    String toBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, toFundId);
    String toLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, toFundId);


    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetBefore = getBudgetAndValidate(toBudgetEndpointWithQueryParams);

    Ledger fromLedger = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Ledger toLedger = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    String fromLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, fromLedger.getId(), fY);
    String toLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, toLedger.getId(), fY);

    LedgerFY fromLedgerFYBefore = getLedgerFYAndValidate(fromLedgerFYEndpointWithQueryParams);
    LedgerFY toLedgerFYBefore = getLedgerFYAndValidate(toLedgerFYEndpointWithQueryParams);


    // create Allocation
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);
    LedgerFY fromLedgerFYAfter = getLedgerFYAndValidate(fromLedgerFYEndpointWithQueryParams);
    LedgerFY toLedgerFYAfter = getLedgerFYAndValidate(toLedgerFYEndpointWithQueryParams);

    // check source budget and ledger totals
    final Double amount = jsonTx.getDouble("amount");
    double expectedBudgetsAvailable;
    double expectedBudgetsAllocated;
    double expectedLedgersAvailable;
    double expectedLedgersAllocated;

    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      expectedBudgetsAllocated = subtractValues(fromBudgetBefore.getAllocated(), amount);
      expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);

      expectedLedgersAllocated = subtractValues(fromLedgerFYBefore.getAllocated(), amount);
      expectedLedgersAvailable = subtractValues(fromLedgerFYBefore.getAvailable(), amount);

      assertEquals(expectedBudgetsAllocated, fromBudgetAfter.getAllocated());
      assertEquals(expectedBudgetsAvailable, fromBudgetAfter.getAvailable());

      assertEquals(expectedLedgersAllocated, fromLedgerFYAfter.getAllocated());
      assertEquals(expectedLedgersAvailable, fromLedgerFYAfter.getAvailable());
    }

    // check destination budget and ledger totals
    expectedBudgetsAllocated = sumValues(toBudgetBefore.getAllocated(), amount);
    expectedBudgetsAvailable = sumValues(toBudgetBefore.getAvailable(), amount);

    expectedLedgersAllocated = sumValues(toLedgerFYBefore.getAllocated(), amount);
    expectedLedgersAvailable = sumValues(toLedgerFYBefore.getAvailable(), amount);

    assertEquals(expectedBudgetsAvailable, toBudgetAfter.getAvailable());
    assertEquals(expectedBudgetsAllocated, toBudgetAfter.getAllocated());

    assertEquals(expectedLedgersAvailable, toLedgerFYAfter.getAvailable());
    assertEquals(expectedLedgersAllocated, toLedgerFYAfter.getAllocated());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateAllocationWithDestinationFundEmpty() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("id");
    jsonTx.remove("toFundId");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");

    // prepare budget/ledger queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String fromLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, fromFundId);

    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Ledger fromLedgerBefore = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);

    // try to create Allocation
    given()
      .header(TRANSACTION_TENANT_HEADER)
      .accept(ContentType.TEXT)
      .contentType(ContentType.JSON)
      .body(transactionSample)
      .log().all()
      .post(storageUrl(TRANSACTION_ENDPOINT))
      .then()
      .statusCode(500);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Ledger fromLedgerAfter = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);

    // verify budget and ledger values not changed
    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      assertEquals(fromBudgetBefore.getAllocated(), fromBudgetAfter.getAllocated());
      assertEquals(fromBudgetBefore.getAvailable(), fromBudgetAfter.getAvailable());
      assertEquals(fromBudgetBefore.getUnavailable() , fromBudgetAfter.getUnavailable());

      assertEquals(fromLedgerBefore.getAllocated(), fromLedgerAfter.getAllocated());
      assertEquals(fromLedgerBefore.getAvailable(), fromLedgerAfter.getAvailable());
      assertEquals(fromLedgerBefore.getUnavailable() , fromLedgerAfter.getUnavailable());
    }

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateAllocationWithSourceBudgetNotExist() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("id");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");
    String toFundId = jsonTx.getString("toFundId");

    // prepare budget/ledger queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String fromLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, fromFundId);
    String toBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, toFundId);
    String toLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, toFundId);

    Budget fromBudget = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    deleteData(GROUP_FUND_FY.getEndpointWithId(), "77cd0046-e4f1-4e4f-9024-adf0b0039d09", TRANSACTION_TENANT_HEADER).then().statusCode(204);
    deleteData(BUDGET.getEndpointWithId(), fromBudget.getId(), TRANSACTION_TENANT_HEADER).then().statusCode(204);
    Ledger fromLedgerBefore = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Budget toBudgetBefore = getBudgetAndValidate(toBudgetEndpointWithQueryParams);
    Ledger toLedgerBefore = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    // try to create Allocation
    given()
      .header(TRANSACTION_TENANT_HEADER)
      .accept(ContentType.TEXT)
      .contentType(ContentType.JSON)
      .body(transactionSample)
      .log().all()
      .post(storageUrl(TRANSACTION_ENDPOINT))
      .then()
      .statusCode(500);

    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);
    Ledger fromLedgerAfter = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Ledger toLedgerAfter = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    // verify budget and ledger values not changed
    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      assertEquals(toBudgetBefore.getAllocated(), toBudgetAfter.getAllocated());
      assertEquals(toBudgetBefore.getAvailable(), toBudgetAfter.getAvailable());
      assertEquals(toBudgetBefore.getUnavailable() , toBudgetAfter.getUnavailable());

      assertEquals(fromLedgerBefore.getAllocated(), fromLedgerAfter.getAllocated());
      assertEquals(fromLedgerBefore.getAvailable(), fromLedgerAfter.getAvailable());
      assertEquals(fromLedgerBefore.getUnavailable() , fromLedgerAfter.getUnavailable());

      assertEquals(toLedgerBefore.getAllocated(), toLedgerAfter.getAllocated());
      assertEquals(toLedgerBefore.getAvailable(), toLedgerAfter.getAvailable());
      assertEquals(toLedgerBefore.getUnavailable() , toLedgerAfter.getUnavailable());
    }

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateAllocationWithSourceLedgerFYNotExist() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("id");


    String fY = "8da275b8-e099-49a1-9a31-b8241ff26ffc";
    jsonTx.put("fiscalYearId", fY);
    String fromFundId = "f31a36de-fcf8-44f9-87ef-a55d06ad21ae";
    jsonTx.put("fromFundId", fromFundId);
    String toFundId = "e54b1f4d-7d05-4b1a-9368-3c36b75d8ac6";
    jsonTx.put("toFundId", toFundId);
    String transactionSample = jsonTx.toString();

    // try to create Allocation
    given()
      .header(TRANSACTION_TENANT_HEADER)
      .accept(ContentType.TEXT)
      .contentType(ContentType.JSON)
      .body(transactionSample)
      .log().all()
      .post(storageUrl(TRANSACTION_ENDPOINT))
      .then()
      .statusCode(500);

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateTransfer() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/transfer.json"));
    jsonTx.remove("id");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");
    String toFundId = jsonTx.getString("toFundId");

    // prepare budget/ledger queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String fromLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, fromFundId);
    String toBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, toFundId);
    String toLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, toFundId);

    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetBefore = getBudgetAndValidate(toBudgetEndpointWithQueryParams);

    Ledger fromLedger = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Ledger toLedger = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    String fromLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, fromLedger.getId(), fY);
    String toLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, toLedger.getId(), fY);

    LedgerFY fromLedgerFYBefore = getLedgerFYAndValidate(fromLedgerFYEndpointWithQueryParams);
    LedgerFY toLedgerFYBefore = getLedgerFYAndValidate(toLedgerFYEndpointWithQueryParams);

    // create Allocation
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);
    LedgerFY fromLedgerFYAfter = getLedgerFYAndValidate(fromLedgerFYEndpointWithQueryParams);
    LedgerFY toLedgerFYAfter = getLedgerFYAndValidate(toLedgerFYEndpointWithQueryParams);

    // check source budget and ledger totals
    final Double amount = jsonTx.getDouble("amount");
    double expectedBudgetsAvailable;
    double expectedLedgersAvailable;

    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);

      expectedLedgersAvailable = subtractValues(fromLedgerFYBefore.getAvailable(), amount);

      assertEquals(expectedBudgetsAvailable, fromBudgetAfter.getAvailable());
      assertEquals(fromBudgetBefore.getUnavailable() , fromBudgetAfter.getUnavailable());

      assertEquals(expectedLedgersAvailable, fromLedgerFYAfter.getAvailable());
      assertEquals(fromLedgerFYBefore.getUnavailable() , fromLedgerFYAfter.getUnavailable());
    }

    // check destination budget and ledger totals
    expectedBudgetsAvailable = sumValues(toBudgetBefore.getAvailable(), amount);
    expectedLedgersAvailable = sumValues(toLedgerFYBefore.getAvailable(), amount);

    assertEquals(expectedBudgetsAvailable, toBudgetAfter.getAvailable());
    assertEquals(expectedLedgersAvailable, toLedgerFYAfter.getAvailable());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }


  @Test
  void testCreateEncumbranceAllOrNothingIdempotent() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);
    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 2);
    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");

    Transaction encumbrance1 = jsonTx.mapTo(Transaction.class);
    encumbrance1.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Transaction encumbrance2 = jsonTx.mapTo(Transaction.class);
    encumbrance2.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance2.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());
    String fY = encumbrance1.getFiscalYearId();
    String fromFundId = encumbrance1.getFromFundId();


    // prepare budget/ledger queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String fromLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, fromFundId);


    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Ledger fromLedger = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);

    String fromLedgerFYEndpointWithQueryParams = String.format(LEDGER_FYS_ENDPOINT, fromLedger.getId(), fY);
    LedgerFY fromLedgerFYBefore = getLedgerFYAndValidate(fromLedgerFYEndpointWithQueryParams);

    // create 1st Encumbrance, expected number is 2
    String encumbrance1Id = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(encumbrance1).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    // create 2nd Encumbrance
    String encumbrance2Id = postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(encumbrance2).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // 2 encumbrances appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    LedgerFY fromLedgerFYAfter = getLedgerFYAndValidate(fromLedgerFYEndpointWithQueryParams);

    // check source budget and ledger totals
    final double amount = sumValues(encumbrance1.getAmount(), encumbrance2.getAmount());
    double expectedBudgetsAvailable;
    double expectedBudgetsUnavailable;
    double expectedBudgetsEncumbered;

    double expectedLedgersAvailable;
    double expectedLedgersUnavailable;


    expectedBudgetsEncumbered = sumValues(fromBudgetBefore.getEncumbered(), amount);
    expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);
    expectedBudgetsUnavailable = sumValues(fromBudgetBefore.getUnavailable(), amount);

    expectedLedgersAvailable = subtractValues(fromLedgerFYBefore.getAvailable(), amount);
    expectedLedgersUnavailable = sumValues(fromLedgerFYBefore.getUnavailable(), amount);

    assertEquals(expectedBudgetsEncumbered, fromBudgetAfter.getEncumbered());
    assertEquals(expectedBudgetsAvailable , fromBudgetAfter.getAvailable());
    assertEquals(expectedBudgetsUnavailable, fromBudgetAfter.getUnavailable());

    assertEquals(expectedLedgersAvailable, fromLedgerFYAfter.getAvailable());
    assertEquals(expectedLedgersUnavailable , fromLedgerFYAfter.getUnavailable());


    //create same encumbrances again
    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(encumbrance1).encodePrettily(), TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(encumbrance2).encodePrettily(), TRANSACTION_TENANT_HEADER)
      .then()
      .statusCode(201);

    // check source budget and ledger totals not changed
    assertEquals(expectedBudgetsEncumbered, fromBudgetAfter.getEncumbered());
    assertEquals(expectedBudgetsAvailable , fromBudgetAfter.getAvailable());
    assertEquals(expectedBudgetsUnavailable, fromBudgetAfter.getUnavailable());

    assertEquals(expectedLedgersAvailable, fromLedgerFYAfter.getAvailable());
    assertEquals(expectedLedgersUnavailable , fromLedgerFYAfter.getUnavailable());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateEncumbranceWithoutSummary() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);
    String orderId = UUID.randomUUID().toString();

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400);

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateEncumbranceWithoutBudget() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, false, false);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 2);
    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400).body(containsString(BUDGET_NOT_FOUND_FOR_TRANSACTION));

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  private void createOrderSummary(String orderId, int encumbranceNumber) throws MalformedURLException {
    OrderTransactionSummary summary = new OrderTransactionSummary().withId(orderId).withNumTransactions(encumbranceNumber);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(summary)
      .encodePrettily(), TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateEncumbranceWithMissedRequiredFields() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));

    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.setEncumbrance(null);
    encumbrance.setFromFundId(null);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    Errors errors = postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(422).extract().as(Errors.class);
    assertThat(errors.getErrors(), hasSize(2));
    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateEncumbrancesDuplicateInTemporaryTable() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);
    String orderId = UUID.randomUUID().toString();

    createOrderSummary(orderId, 2);
    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);

    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();

    String encumbrance1Id = postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201).extract().as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201);

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateEncumbrancesDuplicateInTransactionTable() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId,  2);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");

    Transaction encumbrance1 = jsonTx.mapTo(Transaction.class);
    encumbrance1.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Transaction encumbrance2 = jsonTx.mapTo(Transaction.class);
    encumbrance2.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance2.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());

    String transactionSample1 = JsonObject.mapFrom(encumbrance1).encodePrettily();

    // create 1st Encumbrance, expected number is 2
    String encumbrance1Id = postData(TRANSACTION_ENDPOINT, transactionSample1, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);


    String transactionSample2 = JsonObject.mapFrom(encumbrance2).encodePrettily();

    // create 2nd Encumbrance
    String encumbrance2Id = postData(TRANSACTION_ENDPOINT, transactionSample2, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // 2 encumbrances appear in transaction table, temp transactions deleted
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    encumbrance1Id = postData(TRANSACTION_ENDPOINT, transactionSample1, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    // create 2nd Encumbrance
    postData(TRANSACTION_ENDPOINT, transactionSample2, TRANSACTION_TENANT_HEADER)
      .then()
      .statusCode(201);

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testUpdateEncumbranceAllOrNothing() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 2);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance1 = jsonTx.mapTo(Transaction.class);
    encumbrance1.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Transaction encumbrance2 = jsonTx.mapTo(Transaction.class);
    encumbrance2.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance2.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());

    String fY = encumbrance1.getFiscalYearId();
    String fromFundId = encumbrance1.getFromFundId();

    String transactionSample = JsonObject.mapFrom(encumbrance1).encodePrettily();
    // prepare budget queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);

    // create 1st Encumbrance, expected number is 2
    String encumbrance1Id = postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance do not appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(404);

    transactionSample = JsonObject.mapFrom(encumbrance2).encodePrettily();

    // create 2nd Encumbrance
    String encumbrance2Id = postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // 2 encumbrances appear in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);

    double releasedAmount = encumbrance1.getAmount();
    double amountAwaitingPaymentDif = 5.5;
    encumbrance1.getEncumbrance().setStatus(Encumbrance.Status.RELEASED);
    encumbrance2.setAmount(100d);
    encumbrance2.getEncumbrance().setStatus(Encumbrance.Status.UNRELEASED);
    encumbrance2.getEncumbrance().setAmountAwaitingPayment(sumValues(encumbrance2.getEncumbrance().getAmountAwaitingPayment(), 5.5));

    // First encumbrance update, save to temp table, changes won't get to transaction table
    putData(TRANSACTION.getEndpointWithId(), encumbrance1Id, JsonObject.mapFrom(encumbrance1).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);
    Transaction transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(Encumbrance.Status.UNRELEASED, transaction1FromStorage.getEncumbrance().getStatus());
    assertEquals(transaction1FromStorage.getAmount(), releasedAmount);

    // Second encumbrance update, changes for two encumbrances will get to transaction table
    putData(TRANSACTION.getEndpointWithId(), encumbrance2Id, JsonObject.mapFrom(encumbrance2).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);
    transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    Transaction transaction2FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance2Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    assertEquals(Encumbrance.Status.RELEASED, transaction1FromStorage.getEncumbrance().getStatus());
    assertEquals(0d, transaction1FromStorage.getAmount());
    assertEquals(transaction2FromStorage.getEncumbrance().getAmountAwaitingPayment(), encumbrance2.getEncumbrance().getAmountAwaitingPayment());
    double expectedAmount = subtractValues(encumbrance2.getEncumbrance().getInitialAmountEncumbered(), encumbrance2.getEncumbrance().getAmountAwaitingPayment());
    expectedAmount = subtractValues(expectedAmount, encumbrance2.getEncumbrance().getAmountExpended());
    assertEquals(expectedAmount, transaction2FromStorage.getAmount());

    Budget fromBudgetAfterUpdate = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);

    double expectedBudgetsEncumbered = subtractValues(fromBudgetBefore.getEncumbered(), releasedAmount);
    expectedBudgetsEncumbered = subtractValues(expectedBudgetsEncumbered, amountAwaitingPaymentDif);
    double expectedBudgetsAvailable = sumValues(fromBudgetBefore.getAvailable(), releasedAmount);
    double expectedBudgetsUnavailable = subtractValues(fromBudgetBefore.getUnavailable(), releasedAmount);
    expectedBudgetsUnavailable = expectedBudgetsUnavailable < 0 ? 0 : expectedBudgetsUnavailable;
    double expectedAwaitingPayment = sumValues(fromBudgetBefore.getAwaitingPayment(), amountAwaitingPaymentDif);

    assertEquals(expectedBudgetsEncumbered, fromBudgetAfterUpdate.getEncumbered());
    assertEquals(expectedBudgetsAvailable , fromBudgetAfterUpdate.getAvailable());
    assertEquals(expectedBudgetsUnavailable, fromBudgetAfterUpdate.getUnavailable());
    assertEquals(expectedAwaitingPayment, fromBudgetAfterUpdate.getAwaitingPayment());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testUpdateAlreadyReleasedEncumbranceBudgetNotUpdated() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    String orderId = UUID.randomUUID().toString();
      createOrderSummary(orderId, 1);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);
    encumbrance.getEncumbrance().setStatus(Encumbrance.Status.RELEASED);
    encumbrance.setAmount(0d);
    encumbrance.getEncumbrance().setAmountAwaitingPayment(10d);

    String fY = encumbrance.getFiscalYearId();
    String fromFundId = encumbrance.getFromFundId();

    String transactionSample = JsonObject.mapFrom(encumbrance).encodePrettily();
    // prepare budget queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);

    // create 1st Encumbrance, expected number is 2
    String encumbrance1Id = postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class).getId();

    // encumbrance appearS in transaction table
    getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);

    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);

    encumbrance.getEncumbrance().setAmountAwaitingPayment(5d);
    encumbrance.setAmount(2d);

    putData(TRANSACTION.getEndpointWithId(), encumbrance1Id, JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(204);
    Transaction transaction1FromStorage = getDataById(TRANSACTION.getEndpointWithId(), encumbrance1Id, TRANSACTION_TENANT_HEADER).then().statusCode(200).extract().as(Transaction.class);
    assertEquals(5d, transaction1FromStorage.getEncumbrance().getAmountAwaitingPayment());
    assertEquals(2d, transaction1FromStorage.getAmount());
    Budget fromBudgetAfterUpdate = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);

    assertEquals(fromBudgetBefore.getEncumbered(), fromBudgetAfterUpdate.getEncumbered());
    assertEquals(fromBudgetBefore.getAvailable() , fromBudgetAfterUpdate.getAvailable());
    assertEquals(fromBudgetBefore.getUnavailable(), fromBudgetAfterUpdate.getUnavailable());
    assertEquals(fromBudgetBefore.getAwaitingPayment(), fromBudgetAfterUpdate.getAwaitingPayment());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }


  @Test
  void testUpdateEncumbranceNotFound() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    String orderId = UUID.randomUUID().toString();
    createOrderSummary(orderId, 2);

    JsonObject jsonTx = new JsonObject(getFile(ENCUMBRANCE_SAMPLE));
    jsonTx.remove("id");
    Transaction encumbrance = jsonTx.mapTo(Transaction.class);
    encumbrance.getEncumbrance().setSourcePurchaseOrderId(orderId);

    // Try to update non-existent transaction
    putData(TRANSACTION.getEndpointWithId(), UUID.randomUUID().toString(), JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER).then().statusCode(404);
    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }


  private Ledger getLedgerAndValidate(String endpoint) throws MalformedURLException {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(LEDGERS, hasSize(1))
      .extract()
      .as(LedgerCollection.class).getLedgers().get(0);
  }

  private LedgerFY getLedgerFYAndValidate(String endpoint) throws MalformedURLException {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body("ledgerFY", hasSize(1))
      .extract()
      .as(LedgerFYCollection.class).getLedgerFY().get(0);
  }

  private Budget getBudgetAndValidate(String endpoint) throws MalformedURLException {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(BUDGETS, hasSize(1))
      .extract()
      .as(BudgetCollection.class).getBudgets().get(0);
  }

  private double subtractValues(double d1, double d2) {
    return BigDecimal.valueOf(d1).subtract(BigDecimal.valueOf(d2)).doubleValue();
  }

  private double sumValues(double d1, double d2) {
    return BigDecimal.valueOf(d1).add(BigDecimal.valueOf(d2)).doubleValue();
  }
}
