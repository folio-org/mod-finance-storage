package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.ALLOCATION_TRANSACTION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

public class TransactionTest extends TestBase {

  protected static final String TRANSACTION_ENDPOINT = ALLOCATION_TRANSACTION.getEndpoint();
  protected static final String TRANSACTION_ENDPOINT_BY_ID = ALLOCATION_TRANSACTION.getEndpointWithId();
  protected static final String TRANSACTION_TEST_TENANT = "transaction_test_tenant";
  protected static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  private static final String FY_FUND_QUERY = "?query=fiscalYearId==%s AND fundId==%s";
  public static final String ALLOCATION_SAMPLE = "data/transactions/zallocation_AFRICAHIST-FY22_ANZHIST-FY22.json";
  public static final String TRANSFER_NOT_ENOUGH_MONEY_ERROR_TEXT = "Transfer was not successful. There is not enough money Available in the budget to complete this Transfer";

  static String BUDGETS_QUERY = BUDGET.getEndpoint() + FY_FUND_QUERY;
  static final String BUDGETS = "budgets";
  public static final String FISCAL_YEAR_18_SAMPLE_PATH = "data/fiscal-years/fy18.json";
  public static final String LEDGER_MAIN_LIBRARY_SAMPLE_PATH = "data/ledgers/MainLibrary.json";
  public static final String ALLOCATION_FROM_FUND_SAMPLE_PATH = "data/funds/CANLATHIST.json";
  public static final String ALLOCATION_TO_FUND_SAMPLE_PATH = "data/funds/ANZHIST.json";
  public static final String ALLOCATION_FROM_BUDGET_SAMPLE_PATH = "data/budgets/CANLATHIST-FY22-closed.json";
  public static final String ALLOCATION_TO_BUDGET_SAMPLE_PATH = "data/budgets/ANZHIST-FY22.json";
  public static final String ALLOCATION_SAMPLE_PATH = "data/transactions/allocations/allocation_CANLATHIST-FY22.json";
  public static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT = "/finance-storage/order-transaction-summaries";
  private static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID = ORDER_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";
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
  void testCreateAllocation() throws MalformedURLException {

    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(FISCAL_YEAR, FISCAL_YEAR_18_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER_MAIN_LIBRARY_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, ALLOCATION_TO_FUND_SAMPLE_PATH),
      Pair.of(FUND, ALLOCATION_FROM_FUND_SAMPLE_PATH),
      Pair.of(BUDGET, ALLOCATION_TO_BUDGET_SAMPLE_PATH),
      Pair.of(BUDGET, ALLOCATION_FROM_BUDGET_SAMPLE_PATH),
      Pair.of(ALLOCATION_TRANSACTION, ALLOCATION_SAMPLE_PATH));

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("id");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");
    String toFundId = jsonTx.getString("toFundId");

    // prepare budget queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String toBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, toFundId);

    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetBefore = getBudgetAndValidate(toBudgetEndpointWithQueryParams);


    // create Allocation
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);

    // check source budget totals
    final Double amount = jsonTx.getDouble("amount");
    double expectedBudgetsAvailable;
    double expectedBudgetsAllocated;

    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      expectedBudgetsAllocated = subtractValues(fromBudgetBefore.getAllocated(), amount);
      expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);

      assertEquals(expectedBudgetsAllocated, fromBudgetAfter.getAllocated());
      assertEquals(expectedBudgetsAvailable, fromBudgetAfter.getAvailable());

    }

    // check destination budget totals
    expectedBudgetsAllocated = sumValues(toBudgetBefore.getAllocated(), amount);
    expectedBudgetsAvailable = sumValues(toBudgetBefore.getAvailable(), amount);

    assertEquals(expectedBudgetsAvailable, toBudgetAfter.getAvailable());
    assertEquals(expectedBudgetsAllocated, toBudgetAfter.getAllocated());
  }

  @Test
  void testCreateAllocationWithSourceBudgetNotExist() throws MalformedURLException {
    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(FISCAL_YEAR, FISCAL_YEAR_18_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER_MAIN_LIBRARY_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, ALLOCATION_TO_FUND_SAMPLE_PATH),
      Pair.of(FUND, ALLOCATION_FROM_FUND_SAMPLE_PATH),
      Pair.of(BUDGET, ALLOCATION_TO_BUDGET_SAMPLE_PATH));

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("id");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String toFundId = jsonTx.getString("toFundId");

    // prepare budget queries
    String toBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, toFundId);

    Budget toBudgetBefore = getBudgetAndValidate(toBudgetEndpointWithQueryParams);

    // try to create Allocation
    given()
      .header(TRANSACTION_TENANT_HEADER)
      .accept(ContentType.TEXT)
      .contentType(ContentType.JSON)
      .body(transactionSample)
      .log().all()
      .post(storageUrl(TRANSACTION_ENDPOINT))
      .then()
      .statusCode(400);

    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);

    // verify budget values not changed
    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      assertEquals(toBudgetBefore.getAllocated(), toBudgetAfter.getAllocated());
      assertEquals(toBudgetBefore.getAvailable(), toBudgetAfter.getAvailable());
      assertEquals(toBudgetBefore.getUnavailable() , toBudgetAfter.getUnavailable());
    }

  }

  @Test
  void testCreateTransfer() throws MalformedURLException {

    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(FISCAL_YEAR, FISCAL_YEAR_18_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER_MAIN_LIBRARY_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, ALLOCATION_TO_FUND_SAMPLE_PATH),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()),
      Pair.of(BUDGET, ALLOCATION_TO_BUDGET_SAMPLE_PATH));

    JsonObject jsonAllocation = new JsonObject(getFile("data/transactions/allocations/allocation_AFRICAHIST-FY22.json"));
    jsonAllocation.remove("id");
    String allocationSample = jsonAllocation.toString();

    postData(TRANSACTION_ENDPOINT, allocationSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/transfers/transfer.json"));
    jsonTx.remove("id");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");
    String toFundId = jsonTx.getString("toFundId");

    // prepare budget queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String toBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, toFundId);

    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetBefore = getBudgetAndValidate(toBudgetEndpointWithQueryParams);

    // create Transfer
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);
    // check source budget totals
    final Double amount = jsonTx.getDouble("amount");
    double expectedBudgetsAvailable;

    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))) {
      expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);


      assertEquals(expectedBudgetsAvailable, fromBudgetAfter.getAvailable());
      assertEquals(fromBudgetBefore.getUnavailable() , fromBudgetAfter.getUnavailable());
    }

    // check destination budget totals
    expectedBudgetsAvailable = sumValues(toBudgetBefore.getAvailable(), amount);

    assertEquals(expectedBudgetsAvailable, toBudgetAfter.getAvailable());

  }


  @Test
  void testCreateTransferThatDoesNotHaveEnoughMoney() throws MalformedURLException {

    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(FISCAL_YEAR, FISCAL_YEAR_18_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER_MAIN_LIBRARY_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, ALLOCATION_TO_FUND_SAMPLE_PATH),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()),
      Pair.of(BUDGET, ALLOCATION_TO_BUDGET_SAMPLE_PATH));

    JsonObject jsonAllocation = new JsonObject(getFile("data/transactions/allocations/allocation_AFRICAHIST-FY22.json"));
    jsonAllocation.remove("id");
    String allocationSample = jsonAllocation.toString();

    postData(TRANSACTION_ENDPOINT, allocationSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/transfers/transfer.json"));
    jsonTx.remove("id");
    jsonTx.put("amount","21001");
    String transactionSample = jsonTx.toString();

    // create Transfer
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(TRANSFER_NOT_ENOUGH_MONEY_ERROR_TEXT));

  }

  protected Budget getBudgetAndValidate(String endpoint) throws MalformedURLException {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(BUDGETS, hasSize(1))
      .extract()
      .as(BudgetCollection.class).getBudgets().get(0);
  }

  @Test
  void testUpdateEncumbranceConflict() throws MalformedURLException {
    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    String orderId = UUID.randomUUID().toString();
    String orderLineId = UUID.randomUUID().toString();
    OrderTransactionSummary postSummary = new OrderTransactionSummary()
      .withId(orderId)
      .withNumTransactions(1);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(postSummary).encodePrettily(), TRANSACTION_TENANT_HEADER)
      .then().statusCode(201);

    String encumbranceId = UUID.randomUUID().toString();
    Transaction encumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(FUND.getId())
      .withTransactionType(Transaction.TransactionType.ENCUMBRANCE)
      .withAmount(10.0)
      .withFiscalYearId(FISCAL_YEAR.getId())
      .withSource(Transaction.Source.PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withOrderType(Encumbrance.OrderType.ONE_TIME)
        .withOrderStatus(Encumbrance.OrderStatus.OPEN)
        .withSourcePurchaseOrderId(orderId)
        .withSourcePoLineId(orderLineId)
        .withInitialAmountEncumbered(10d)
        .withSubscription(false)
        .withReEncumber(false));
    postData(TRANSACTION_ENDPOINT, JsonObject.mapFrom(encumbrance).encodePrettily(), TRANSACTION_TENANT_HEADER)
      .then().statusCode(201);

    Transaction encumbrance2 = JsonObject.mapFrom(encumbrance).mapTo(Transaction.class)
      .withAmount(9.0)
      .withVersion(1);
    OrderTransactionSummary putSummary1 = new OrderTransactionSummary()
      .withId(orderId)
      .withNumTransactions(1);
    putData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, orderId, JsonObject.mapFrom(putSummary1).encodePrettily(),
        TRANSACTION_TENANT_HEADER)
      .then().statusCode(204);
    putData(TRANSACTION_ENDPOINT_BY_ID, encumbranceId, JsonObject.mapFrom(encumbrance2).encodePrettily(), TRANSACTION_TENANT_HEADER)
      .then().statusCode(204);

    Transaction encumbrance3 = JsonObject.mapFrom(encumbrance).mapTo(Transaction.class)
      .withAmount(8.0);
    OrderTransactionSummary putSummary2 = new OrderTransactionSummary()
      .withId(orderId)
      .withNumTransactions(1);
    putData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, orderId, JsonObject.mapFrom(putSummary2).encodePrettily(),
        TRANSACTION_TENANT_HEADER)
      .then().statusCode(204);
    putData(TRANSACTION_ENDPOINT_BY_ID, encumbranceId, JsonObject.mapFrom(encumbrance3).encodePrettily(), TRANSACTION_TENANT_HEADER)
      .then().statusCode(409);
  }

}
