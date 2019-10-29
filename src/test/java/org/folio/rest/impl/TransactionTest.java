package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.net.MalformedURLException;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.utils.TestEntities;
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
  private static String BUDGETS_QUERY = BUDGET.getEndpoint() + FY_FUND_QUERY;
  private static String LEDGERS_QUERY = LEDGER.getEndpoint() + LEDGER_QUERY;
  private static final String BUDGETS = "budgets";
  private static final String LEDGERS = "ledgers";

  @Test
  void testCreateAllocation() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/allocation.json"));
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

    Ledger fromLedgerBefore = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Ledger toLedgerBefore = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    // create Allocation
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);
    Ledger fromLedgerAfter = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Ledger toLedgerAfter = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    // check source budget and ledger totals
    final Double amount = jsonTx.getDouble("amount");
    double expectedBudgetsAvailable;
    double expectedBudgetsAllocated;
    double expectedLedgersAvailable;
    double expectedLedgersAllocated;

    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      expectedBudgetsAllocated = subtractValues(fromBudgetBefore.getAllocated(), amount);
      expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);

      expectedLedgersAllocated = subtractValues(fromLedgerBefore.getAllocated(), amount);
      expectedLedgersAvailable = subtractValues(fromLedgerBefore.getAvailable(), amount);

      assertEquals(expectedBudgetsAllocated, fromBudgetAfter.getAllocated());
      assertEquals(expectedBudgetsAvailable, fromBudgetAfter.getAvailable());

      assertEquals(expectedLedgersAllocated, fromLedgerAfter.getAllocated());
      assertEquals(expectedLedgersAvailable, fromLedgerAfter.getAvailable());
    }

    // check destination budget and ledger totals
    expectedBudgetsAllocated = sumValues(toBudgetBefore.getAllocated(), amount);
    expectedBudgetsAvailable = sumValues(toBudgetBefore.getAvailable(), amount);

    expectedLedgersAllocated = sumValues(toLedgerBefore.getAllocated(), amount);
    expectedLedgersAvailable = sumValues(toLedgerBefore.getAvailable(), amount);

    assertEquals(expectedBudgetsAvailable, toBudgetAfter.getAvailable());
    assertEquals(expectedBudgetsAllocated, toBudgetAfter.getAllocated());

    assertEquals(expectedLedgersAvailable, toLedgerAfter.getAvailable());
    assertEquals(expectedLedgersAllocated, toLedgerAfter.getAllocated());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testCreateAllocationWithDestinationFundEmpty() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/allocation.json"));
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

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/allocation.json"));
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

    Ledger fromLedgerBefore = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Ledger toLedgerBefore = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    // create Allocation
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Budget toBudgetAfter = getBudgetAndValidate(toBudgetEndpointWithQueryParams);
    Ledger fromLedgerAfter = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);
    Ledger toLedgerAfter = getLedgerAndValidate(toLedgerEndpointWithQueryParams);

    // check source budget and ledger totals
    final Double amount = jsonTx.getDouble("amount");
    double expectedBudgetsAvailable;
    double expectedBudgetsUnavailable;
    double expectedLedgersAvailable;
    double expectedLedgersUnavailable;

    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);
      expectedBudgetsUnavailable = sumValues(fromBudgetBefore.getUnavailable(), amount);

      expectedLedgersAvailable = subtractValues(fromLedgerBefore.getAvailable(), amount);
      expectedLedgersUnavailable = sumValues(fromLedgerBefore.getUnavailable(), amount);

      assertEquals(expectedBudgetsAvailable, fromBudgetAfter.getAvailable());
      assertEquals(expectedBudgetsUnavailable , fromBudgetAfter.getUnavailable());

      assertEquals(expectedLedgersAvailable, fromLedgerAfter.getAvailable());
      assertEquals(expectedLedgersUnavailable , fromLedgerAfter.getUnavailable());
    }

    // check destination budget and ledger totals
    expectedBudgetsAvailable = sumValues(toBudgetBefore.getAvailable(), amount);
    expectedLedgersAvailable = sumValues(toLedgerBefore.getAvailable(), amount);

    assertEquals(expectedBudgetsAvailable, toBudgetAfter.getAvailable());
    assertEquals(expectedLedgersAvailable, toLedgerAfter.getAvailable());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }


  @Test
  void testCreateEncumbrance() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, TRANSACTION.getInitialQuantity(), TRANSACTION_TENANT_HEADER);

    JsonObject jsonTx = new JsonObject(getFile("data/transactions/encumbrance.json"));
    jsonTx.remove("id");
    String transactionSample = jsonTx.toString();

    String fY = jsonTx.getString("fiscalYearId");
    String fromFundId = jsonTx.getString("fromFundId");

    // prepare budget/ledger queries
    String fromBudgetEndpointWithQueryParams = String.format(BUDGETS_QUERY, fY, fromFundId);
    String fromLedgerEndpointWithQueryParams = String.format(LEDGERS_QUERY, fromFundId);


    Budget fromBudgetBefore = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Ledger fromLedgerBefore = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);

    // create Encumbrance
    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget fromBudgetAfter = getBudgetAndValidate(fromBudgetEndpointWithQueryParams);
    Ledger fromLedgerAfter = getLedgerAndValidate(fromLedgerEndpointWithQueryParams);

    // check source budget and ledger totals
    final Double amount = jsonTx.getDouble("amount");
    double expectedBudgetsAvailable;
    double expectedBudgetsUnavailable;
    double expectedBudgetsEncumbered;

    double expectedLedgersAvailable;
    double expectedLedgersUnavailable;

    if (StringUtils.isNotEmpty(jsonTx.getString("fromFundId"))){
      expectedBudgetsEncumbered = sumValues(fromBudgetBefore.getEncumbered(), amount);
      expectedBudgetsAvailable = subtractValues(fromBudgetBefore.getAvailable(), amount);
      expectedBudgetsUnavailable = sumValues(fromBudgetBefore.getUnavailable(), amount);

      expectedLedgersAvailable = subtractValues(fromLedgerBefore.getAvailable(), amount);
      expectedLedgersUnavailable = sumValues(fromLedgerBefore.getUnavailable(), amount);

      assertEquals(expectedBudgetsEncumbered, fromBudgetAfter.getEncumbered());
      assertEquals(expectedBudgetsAvailable , fromBudgetAfter.getAvailable());
      assertEquals(expectedBudgetsUnavailable, fromBudgetAfter.getUnavailable());

      assertEquals(expectedLedgersAvailable, fromLedgerAfter.getAvailable());
      assertEquals(expectedLedgersUnavailable , fromLedgerAfter.getUnavailable());
    }
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
