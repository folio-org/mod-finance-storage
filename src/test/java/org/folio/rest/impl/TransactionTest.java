package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.getEndpoint;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.LedgerFYCollection;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerFiscalYears;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

class TransactionTest extends TestBase {
  protected static final String TRANSACTION_ENDPOINT = TRANSACTION.getEndpoint();
  private static final String TRANSACTION_TEST_TENANT = "transaction_test_tenant";
  protected static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  private static final String FY_FUND_QUERY = "?query=fiscalYearId==%s AND fundId==%s";
  private static final String LEDGER_QUERY = "?query=fund.id==%s";
  private static final String LEDGER_FY_QUERY = "?query=ledgerId==%s AND fiscalYearId==%s";
  public static final String ALLOCATION_SAMPLE = "data/transactions/allocations/allocation_AFRICAHIST-FY20_ANZHIST-FY20.json";

  static String BUDGETS_QUERY = BUDGET.getEndpoint() + FY_FUND_QUERY;
  private static String LEDGERS_QUERY = LEDGER.getEndpoint() + LEDGER_QUERY;
  public static String LEDGER_FYS_ENDPOINT = getEndpoint(FinanceStorageLedgerFiscalYears.class) + LEDGER_FY_QUERY;
  static final String BUDGETS = "budgets";
  private static final String LEDGERS = "ledgers";
  public static final String FISCAL_YEAR_18_SAMPLE_PATH = "data/fiscal-years/fy18.json";
  public static final String LEDGER_MAIN_LIBRARY_SAMPLE_PATH = "data/ledgers/MainLibrary.json";
  public static final String ALLOCATION_FROM_FUND_SAMPLE_PATH = "data/funds/CANLATHIST.json";
  public static final String ALLOCATION_TO_FUND_SAMPLE_PATH = "data/funds/ANZHIST.json";
  public static final String ALLOCATION_FROM_BUDGET_SAMPLE_PATH = "data/budgets/CANLATHIST-FY20-closed.json";
  public static final String ALLOCATION_TO_BUDGET_SAMPLE_PATH = "data/budgets/ANZHIST-FY20.json";

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
      Pair.of(BUDGET, ALLOCATION_FROM_BUDGET_SAMPLE_PATH));

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
    String fromFundId = jsonTx.getString("fromFundId");
    String toFundId = jsonTx.getString("toFundId");

    // prepare budget/ledger queries
    String fromLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, fromFundId);
    String toBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, toFundId);
    String toLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, toFundId);

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

  }

  @Test
  void testCreateAllocationWithSourceLedgerFYNotExist() throws MalformedURLException {

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


    String fY = new JsonObject(getFile(FISCAL_YEAR_18_SAMPLE_PATH)).getString(ID_FIELD_NAME);
    jsonTx.put("fiscalYearId", fY);
    String fromFundId = new JsonObject(getFile(ALLOCATION_FROM_FUND_SAMPLE_PATH)).getString(ID_FIELD_NAME);
    jsonTx.put("fromFundId", fromFundId);
    String toFundId = new JsonObject(getFile(ALLOCATION_TO_FUND_SAMPLE_PATH)).getString(ID_FIELD_NAME);
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

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/transfers/transfer.json"));
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

  protected Budget getBudgetAndValidate(String endpoint) throws MalformedURLException {
    return getData(endpoint, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(BUDGETS, hasSize(1))
      .extract()
      .as(BudgetCollection.class).getBudgets().get(0);
  }

}
