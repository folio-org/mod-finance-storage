package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

class TransactionTest extends TestBase {
  private static final String TRANSACTION_ENDPOINT = TestEntities.TRANSACTION.getEndpoint();
  private static final String TRANSACTION_ENDPOINT_WITH_ID = TestEntities.TRANSACTION.getEndpointWithId();
  private static final String TRANSACTION_TEST_TENANT = "transaction_test_tenant";
  private static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  @Test
  void testCreateAllocation() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);

    assertEquals(1, 1);
    // search with invalid cql query
    deleteTenant(TRANSACTION_TENANT_HEADER);

  }

  @Test
  void testCreateAllocation2() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);
    verifyCollectionQuantity(TRANSACTION_ENDPOINT, 5, TRANSACTION_TENANT_HEADER);

    JsonObject jsonTransaction = new JsonObject(getFile("data/transactions/allocation.json"));
    jsonTransaction.remove("id");
    String transactionSample = jsonTransaction.toString();

    String budgetQuery = "?query=fiscalYearId==684b5dc5-92f6-4db7-b996-b549d88f5e4e and fundId==67cd0046-e4f1-4e4f-9024-adf0b0039d09";
    String budgetEndpointWithQuery = TestEntities.BUDGET.getEndpoint() + budgetQuery;

    logger.info("123");
    BudgetCollection budgetBefore = getData(budgetEndpointWithQuery, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .body()
      .as(BudgetCollection.class);

    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .body()
      .as(Transaction.class);

    BudgetCollection budgetAfter = getData(budgetEndpointWithQuery, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .extract()
      .body()
      .as(BudgetCollection.class);

    assertEquals(budgetBefore.getBudgets().get(0).getAvailable() - jsonTransaction.getDouble("amount") , budgetAfter.getBudgets().get(0).getAvailable());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);

    // search with invalid cql query

  }
}
