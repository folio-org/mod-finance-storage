package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.TransactionsSummariesTest.ORDERS_SUMMARY_SAMPLE;
import static org.folio.rest.impl.TransactionsSummariesTest.ORDER_TRANSACTION_SUMMARIES_ENDPOINT;
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
import static org.folio.rest.utils.TestEntities.TRANSACTION;
import static org.folio.service.budget.BudgetService.TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
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

  private static final String BUDGET_ENDPOINT = TestEntities.BUDGET.getEndpoint();
  private static final String BUDGET_TEST_TENANT = "budget_test_tenant";
  private static final Header BUDGET_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, BUDGET_TEST_TENANT);
  private static final String ENCUMBR_SAMPLE = "data/transactions/encumbrances/encumbrance_AFRICAHIST_306857_1.json";
  private static TenantJob tenantJob;

  @AfterAll
  static void deleteTable() {
    deleteTenant(tenantJob, BUDGET_TENANT_HEADER);
  }

  @Test
  void testGetQuery() throws Exception {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, true, true);

    // search for GET
    verifyCollectionQuantity(BUDGET_ENDPOINT, 21, BUDGET_TENANT_HEADER);

    // search with fields from "fund"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Inactive", 2, BUDGET_TENANT_HEADER);
    // search with fields from "FY"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fiscalYear.name==FY19", 3, BUDGET_TENANT_HEADER);
    // search with fields from "ledgers"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=ledger.name==Ongoing", 7, BUDGET_TENANT_HEADER);
    // complex query
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Active AND ledger.name==Ongoing AND fiscalYear.code==FY2022", 4, BUDGET_TENANT_HEADER);

    // search with invalid cql query
    testInvalidCQLQuery(BUDGET_ENDPOINT + "?query=invalid-query");
    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testAbleToDeleteBudgetWithExistingOnlyAllocationTransactions() throws Exception {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()),
      Pair.of(TRANSACTION, TRANSACTION.getPathToSampleFile()));

    deleteData(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).then()
      .statusCode(204);

    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testDeleteBudgetFailedWhenExistOtherThenAllocationTransactions() throws Exception {
    tenantJob = prepareTenant(BUDGET_TENANT_HEADER, false, true);

    givenTestData(BUDGET_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    String orderId = UUID.randomUUID().toString();

    OrderTransactionSummary sample = new JsonObject(getFile(ORDERS_SUMMARY_SAMPLE)).mapTo(OrderTransactionSummary.class);
    sample.setId(orderId);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), BUDGET_TENANT_HEADER).as(OrderTransactionSummary.class);


    Transaction transaction = new JsonObject(getFile(ENCUMBR_SAMPLE)).mapTo(Transaction.class);
    transaction.getEncumbrance().setSourcePurchaseOrderId(orderId);

    postData(TRANSACTION.getEndpoint(), JsonObject.mapFrom(transaction).encodePrettily(), BUDGET_TENANT_HEADER)
      .then()
      .statusCode(201);

    deleteData(BUDGET.getEndpointWithId(), BUDGET.getId(), BUDGET_TENANT_HEADER).then()
      .statusCode(400)
      .body(containsString(TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR));

    purge(BUDGET_TENANT_HEADER);
  }

  @Test
  void testDeleteBudgetWithExitingExpenseClass() throws Exception {
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
  void testDeleteBudgetGroupFundFiscalYearBudgetIdIsCleared() throws Exception {
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
  void testDeleteBudgetWithoutReferenceWithGroupFundFiscalYear() throws Exception {
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
  void testUpdateBudgetConflict() throws Exception {
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
