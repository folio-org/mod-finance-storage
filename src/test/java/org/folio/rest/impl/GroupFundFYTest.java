package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;

import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class GroupFundFYTest extends TestBase {

  private static final Header GROUP_FUND_FY_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "GROUP_FUND_FY");

  @Test
  public void testGetQuery() throws Exception {
    prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // search for GET
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "groups"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History", GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "FY"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==FY2019", GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "fund"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.code==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.fundStatus==Active", 11, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.name=European", 2, GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "fund type"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==Approvals", 1, GROUP_FUND_FY_TENANT_HEADER);

    // search with fields from "ledgers"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=ledger.name==One-time", 11, GROUP_FUND_FY_TENANT_HEADER);

    // search by fields from different tables
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History and fiscalYear.code==FY2019", GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fiscalYear.periodEnd < 2020-01-01 and fund.fundStatus==Inactive and ledger.name==One-time", 1, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fundType.name==Approvals and ledger.name==One-time", 1, GROUP_FUND_FY_TENANT_HEADER);

    // search with invalid cql query
    testInvalidCQLQuery(GROUP_FUND_FY.getEndpoint() + "?query=invalid-query");

    deleteTenant(GROUP_FUND_FY_TENANT_HEADER);
  }
}
