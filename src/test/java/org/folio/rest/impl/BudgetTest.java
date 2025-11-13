package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.util.ErrorCodes.TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.BUDGET_EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.GROUP;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.Json;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.BatchIdCollection;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.util.ErrorCodes;
import org.folio.rest.utils.TestEntities;
import org.hamcrest.beans.HasProperty;
import org.hamcrest.beans.HasPropertyWithValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

public class BudgetTest extends TestBase {

  private static final String ALLOCATION_SAMPLE_PATH = "data/transactions/allocations-8.4.0/allocation_AFRICAHIST-FY25.json";
  private static final String ENCUMBRANCE_SAMPLE_PATH = "data/transactions/encumbrances/encumbrance_AFRICAHIST_306857_1.json";
  private static final String BATCH_TRANSACTION_ENDPOINT = "/finance-storage/transactions/batch-all-or-nothing";
  private static final String BUDGET_ENDPOINT = TestEntities.BUDGET.getEndpoint();
  private static final String BUDGET_BATCH_ENDPOINT = BUDGET_ENDPOINT + "/batch";
  private static final String BUDGET_TEST_TENANT = "budgettesttenantapi";
  private static final Header BUDGET_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, BUDGET_TEST_TENANT);
  private static TenantJob tenantJob;

  @AfterAll
  static void deleteTable() {
    deleteTenant(tenantJob, BUDGET_TENANT_HEADER);
  }

  @Test
  void testGetQuery() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, true, true);

    // search for GET
    verifyCollectionQuantity(BUDGET_ENDPOINT, 24, BUDGET_TENANT_HEADER);

    // search with fields from "fund"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Inactive", 2, BUDGET_TENANT_HEADER);
    // search with fields from "FY"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fiscalYear.name==Fiscal Year 2020", 3, BUDGET_TENANT_HEADER);
    // search with fields from "ledgers"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=ledger.name==Ongoing", 7, BUDGET_TENANT_HEADER);
    // complex query
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Active AND ledger.name==Ongoing AND fiscalYear.code==FY2025", 4, BUDGET_TENANT_HEADER);

    // search with invalid cql query
    testInvalidCQLQuery(BUDGET_ENDPOINT + "?query=invalid-query");
    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testGetBudgetsBatch() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, true, true);

    var allRecords = getData(BUDGET_ENDPOINT, BUDGET_TENANT_HEADER).as(BudgetCollection.class);
    List<String> ids = allRecords.getBudgets().stream()
      .map(Budget::getId)
      .distinct()
      .limit(3)
      .toList();

    var budgetIds = new BatchIdCollection().withIds(ids);
    var budgetCollection = postData(BUDGET_BATCH_ENDPOINT, valueAsString(budgetIds), BUDGET_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract().body()
      .as(BudgetCollection.class);

    assertEquals(3, budgetCollection.getBudgets().size());

    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testAbleToDeleteBudgetWithExistingOnlyAllocationTransactions() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    String allocationSample = getFile(ALLOCATION_SAMPLE_PATH);
    Transaction allocation = Json.decodeValue(allocationSample, Transaction.class);
    Batch batch = new Batch().withTransactionsToCreate(List.of(allocation));
    postData(BATCH_TRANSACTION_ENDPOINT, JsonObject.mapFrom(batch).encodePrettily(), BUDGET_TENANT_HEADER)
      .then()
      .statusCode(204);

    deleteData(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).then()
      .statusCode(204);

    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testDeleteBudgetFailedWhenExistOtherThenAllocationTransactions() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    String orderId = UUID.randomUUID().toString();

    Transaction transaction = new JsonObject(getFile(ENCUMBRANCE_SAMPLE_PATH)).mapTo(Transaction.class);
    transaction.getEncumbrance().setSourcePurchaseOrderId(orderId);

    Batch batch = new Batch().withTransactionsToCreate(List.of(transaction));

    postData(BATCH_TRANSACTION_ENDPOINT, JsonObject.mapFrom(batch).encodePrettily(), BUDGET_TENANT_HEADER)
      .then()
      .statusCode(204);

    deleteData(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR.getDescription()));

    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testDeleteBudgetWithExitingExpenseClass() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()),
      Pair.of(BUDGET_EXPENSE_CLASS, BUDGET_EXPENSE_CLASS.getPathToSampleFile()));

    deleteData(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(ErrorCodes.BUDGET_EXPENSE_CLASS_REFERENCE_ERROR.getCode()));

    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testDeleteBudgetGroupFundFiscalYearBudgetIdIsCleared() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(GROUP, GROUP.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()),
      Pair.of(GROUP_FUND_FY, GROUP_FUND_FY.getPathToSampleFile()));

    GroupFundFiscalYear groupFundFiscalYearBefore = getDataById(GROUP_FUND_FY.getEndpointWithId(), GROUP_FUND_FY.getId(), BUDGET_TENANT_HEADER)
      .as(GroupFundFiscalYear.class);

    assertThat(groupFundFiscalYearBefore, HasPropertyWithValue.hasProperty("budgetId",  is(BUDGET.getId())));

    deleteData(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).then().statusCode(204);

    GroupFundFiscalYear groupFundFiscalYearAfter = getDataById(GROUP_FUND_FY.getEndpointWithId(), GROUP_FUND_FY.getId(), BUDGET_TENANT_HEADER)
      .as(GroupFundFiscalYear.class);

    assertThat(groupFundFiscalYearAfter, HasPropertyWithValue.hasProperty("budgetId",  nullValue()));
    assertThat(groupFundFiscalYearAfter, HasProperty.hasProperty("fundId"));
    assertThat(groupFundFiscalYearAfter, HasProperty.hasProperty("fiscalYearId"));
    assertThat(groupFundFiscalYearAfter, HasProperty.hasProperty("groupId"));

    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testDeleteBudgetWithoutReferenceWithGroupFundFiscalYear() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(GROUP, GROUP.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    deleteData(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).then().statusCode(204);


    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testUpdateBudgetConflict() {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    Budget budget1 = getDataById(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).as(Budget.class);
    Budget budget2 = getDataById(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).as(Budget.class);

    budget1.setAllowableEncumbrance(100.0);

    putData(BUDGET.getEndpointWithId(), BUDGET.getId(), JsonObject.mapFrom(budget1).encodePrettily(), BUDGET_TENANT_HEADER);
    putData(BUDGET.getEndpointWithId(), BUDGET.getId(), JsonObject.mapFrom(budget2).encodePrettily(), BUDGET_TENANT_HEADER)
      .then()
      .statusCode(409);

    purge(BUDGET_TENANT_HEADER);
  }
}
