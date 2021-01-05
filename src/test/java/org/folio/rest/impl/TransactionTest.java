package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

public class TransactionTest extends TestBase {

  protected static final String TRANSACTION_ENDPOINT = TRANSACTION.getEndpoint();
  protected static final String TRANSACTION_TEST_TENANT = "transaction_test_tenant";
  protected static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  private static final String FY_FUND_QUERY = "?query=fiscalYearId==%s AND fundId==%s";
  public static final String ALLOCATION_SAMPLE = "data/transactions/zallocation_AFRICAHIST-FY21_ANZHIST-FY21.json";
  public static final String TRANSFER_NOT_ENOUGH_MONEY_ERROR_TEXT = "Transfer was not successful. There is not enough money Available in the budget to complete this Transfer";

  static String BUDGETS_QUERY = BUDGET.getEndpoint() + FY_FUND_QUERY;
  static final String BUDGETS = "budgets";
  public static final String FISCAL_YEAR_18_SAMPLE_PATH = "data/fiscal-years/fy18.json";
  public static final String LEDGER_MAIN_LIBRARY_SAMPLE_PATH = "data/ledgers/MainLibrary.json";
  public static final String ALLOCATION_FROM_FUND_SAMPLE_PATH = "data/funds/CANLATHIST.json";
  public static final String ALLOCATION_TO_FUND_SAMPLE_PATH = "data/funds/ANZHIST.json";
  public static final String ALLOCATION_FROM_BUDGET_SAMPLE_PATH = "data/budgets/CANLATHIST-FY21-closed.json";
  public static final String ALLOCATION_TO_BUDGET_SAMPLE_PATH = "data/budgets/ANZHIST-FY21.json";
  public static final String ALLOCATION_SAMPLE_PATH = "data/transactions/allocations/allocation_CANLATHIST-FY21.json";

  @BeforeEach
  void prepareData() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, false, true);
  }

  @AfterEach
  void deleteData() throws MalformedURLException {
    deleteTenant(TRANSACTION_TENANT_HEADER);
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
      Pair.of(TRANSACTION, ALLOCATION_SAMPLE_PATH));

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
  void testCreateAllocationWithDestinationFundEmpty() throws MalformedURLException {

    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(FISCAL_YEAR, FISCAL_YEAR_18_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER_MAIN_LIBRARY_SAMPLE_PATH),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, ALLOCATION_FROM_FUND_SAMPLE_PATH),
      Pair.of(BUDGET, ALLOCATION_FROM_BUDGET_SAMPLE_PATH));

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("id");
    jsonTx.remove("toFundId");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");

    // prepare budget query
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);

    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);

    // try to create Allocation
    given()
      .header(TRANSACTION_TENANT_HEADER)
      .accept(ContentType.TEXT)
      .contentType(ContentType.JSON)
      .body(transactionSample)
      .log().all()
      .post(storageUrl(TRANSACTION_ENDPOINT))
      .then()
      .statusCode(422);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);

    // verify budget values not changed
    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      assertEquals(fromBudgetBefore.getAllocated(), fromBudgetAfter.getAllocated());
      assertEquals(fromBudgetBefore.getAvailable(), fromBudgetAfter.getAvailable());
      assertEquals(fromBudgetBefore.getUnavailable() , fromBudgetAfter.getUnavailable());
    }

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

    JsonObject jsonAllocation = new JsonObject(getFile("data/transactions/allocations/allocation_AFRICAHIST-FY21.json"));
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

    JsonObject jsonAllocation = new JsonObject(getFile("data/transactions/allocations/allocation_AFRICAHIST-FY21.json"));
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

}
