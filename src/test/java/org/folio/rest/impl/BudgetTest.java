package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import java.net.MalformedURLException;

import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

class BudgetTest extends TestBase {

  private static final String BUDGET_ENDPOINT = TestEntities.BUDGET.getEndpoint();
  private static final String BUDGET_TEST_TENANT = "budget_test_tenant";
  private static final Header BUDGET_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, BUDGET_TEST_TENANT);

  @Test
  void testGetQuery() throws MalformedURLException {
    prepareTenant(BUDGET_TENANT_HEADER, true, true);

    // search for GET
    verifyCollectionQuantity(BUDGET_ENDPOINT, 21, BUDGET_TENANT_HEADER);

    // search with fields from "fund"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Inactive", 2, BUDGET_TENANT_HEADER);
    // search with fields from "FY"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fiscalYear.name==FY18", 3, BUDGET_TENANT_HEADER);
    // search with fields from "ledgers"
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=ledger.name==Ongoing", 7, BUDGET_TENANT_HEADER);
    // complex query
    verifyCollectionQuantity(BUDGET_ENDPOINT + "?query=fund.fundStatus==Active AND ledger.name==Ongoing AND fiscalYear.code==FY19", 4, BUDGET_TENANT_HEADER);

    // search with invalid cql query
    testInvalidCQLQuery(BUDGET_ENDPOINT + "?query=invalid-query");
    deleteTenant(BUDGET_TENANT_HEADER);
  }
}
