package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;

import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class GroupFundFYTest extends TestBase {

  private static final Header GROUP_FUND_FY_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "groupfundfy");

  @Test
  public void testGetQuery() {
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // search for GET
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "groups"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History", GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "FY"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==FY2025", 12, GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "fund"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.code==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.fundStatus==Active", 12, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.name=European", 2, GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "fund type"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==Approvals", 2, GROUP_FUND_FY_TENANT_HEADER);

    // search with fields from "ledgers"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=ledger.name==One-time", 12, GROUP_FUND_FY_TENANT_HEADER);

    // search by fields from different tables
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History and fiscalYear.code==FY2025", 12, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fiscalYear.periodEnd < 2026-01-01 and fund.fundStatus==Inactive and ledger.name==One-time", 1, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fundType.name==Approvals and ledger.name==One-time", 2, GROUP_FUND_FY_TENANT_HEADER);
    // search with invalid cql query
    testInvalidCQLQuery(GROUP_FUND_FY.getEndpoint() + "?query=invalid-query");

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }


}
