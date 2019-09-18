package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.net.MalformedURLException;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

class TransactionTest extends TestBase {
  private static final String TRANSACTION_ENDPOINT = TestEntities.TRANSACTION.getEndpoint();
  private static final String TRANSACTION_TEST_TENANT = "transaction_test_tenant";
  private static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  private static final String BUDGETS = "budgets";
  private static final String LEDGERS = "ledgers";

  @Test
  void testCreateAllocation() throws MalformedURLException {
    prepareTenant(TRANSACTION_TENANT_HEADER, true, true);

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

    Budget budgetBefore = getData(budgetEndpointWithQuery, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(BUDGETS, hasSize(1))
      .extract()
      .as(BudgetCollection.class).getBudgets().get(0);


    String ledgerQuery = "?query=budget.fiscalYearId==684b5dc5-92f6-4db7-b996-b549d88f5e4e and budget.fundId==67cd0046-e4f1-4e4f-9024-adf0b0039d09";
    String ledgerEndpointWithQuery = TestEntities.LEDGER.getEndpoint() + ledgerQuery;
    Ledger ledgerBefore = getData(ledgerEndpointWithQuery, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(LEDGERS, hasSize(1))
      .extract()
      .as(LedgerCollection.class).getLedgers().get(0);

    postData(TRANSACTION_ENDPOINT, transactionSample, TRANSACTION_TENANT_HEADER).then()
      .statusCode(201)
      .extract()
      .as(Transaction.class);

    Budget budgetAfter = getData(budgetEndpointWithQuery, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(BUDGETS, hasSize(1))
      .extract()
      .as(BudgetCollection.class).getBudgets().get(0);

    Ledger ledgerAfter = getData(ledgerEndpointWithQuery, TRANSACTION_TENANT_HEADER).then()
      .statusCode(200)
      .body(LEDGERS, hasSize(1))
      .extract()
      .as(LedgerCollection.class).getLedgers().get(0);

    final Double amount = jsonTransaction.getDouble("amount");
    double expectedBudgetsAvailable = subtractValues(budgetBefore.getAvailable(), amount);
    double expectedBudgetsAllocated = subtractValues(budgetBefore.getAllocated(), amount);
    double expectedBudgetsUnavailable = sumValues(budgetBefore.getUnavailable(), amount);

    double expectedLedgersAvailable = subtractValues(ledgerBefore.getAvailable(), amount);
    double expectedLedgersAllocated = subtractValues(ledgerBefore.getAllocated(), amount);
    double expectedLedgersUnavailable = sumValues(ledgerBefore.getUnavailable(), amount);

    assertEquals(expectedBudgetsAvailable, budgetAfter.getAvailable());
    assertEquals(expectedBudgetsAllocated, budgetAfter.getAllocated());
    assertEquals(expectedBudgetsUnavailable , budgetAfter.getUnavailable());

    assertEquals(expectedLedgersAvailable, ledgerAfter.getAvailable());
    assertEquals(expectedLedgersAllocated, ledgerAfter.getAllocated());
    assertEquals(expectedLedgersUnavailable , ledgerAfter.getUnavailable());

    // cleanup
    deleteTenant(TRANSACTION_TENANT_HEADER);
  }

  private double subtractValues(double d1, double d2){
    return BigDecimal.valueOf(d1).subtract(BigDecimal.valueOf(d2)).doubleValue();
  }

  private double sumValues(double d1, double d2){
    return BigDecimal.valueOf(d1).add(BigDecimal.valueOf(d2)).doubleValue();
  }
}
