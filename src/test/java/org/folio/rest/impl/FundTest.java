package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.folio.rest.jaxrs.model.BatchIdCollection;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundCollection;
import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class FundTest extends TestBase {

  private static final String FUND_ENDPOINT = FUND.getEndpoint();
  private static final String FUND_BATCH_ENDPOINT = FUND_ENDPOINT + "/batch";
  private static final String FUND_TEST_TENANT = "fundtesttenantapi";
  private static final Header FUND_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, FUND_TEST_TENANT);
  private static TenantJob tenantJob;

  @AfterAll
  static void deleteTable() {
    deleteTenant(tenantJob, FUND_TENANT_HEADER);
  }

  @Test
  void testGetFundsBatch() {
    tenantJob = prepareTenant(FUND_TENANT_HEADER, true, true);

    var allRecords = getData(FUND_ENDPOINT, FUND_TENANT_HEADER).as(FundCollection.class);
    List<String> ids = allRecords.getFunds().stream()
      .map(Fund::getId)
      .distinct()
      .limit(3)
      .toList();

    var fundIds = new BatchIdCollection().withIds(ids);
    var fundCollection = postData(FUND_BATCH_ENDPOINT, valueAsString(fundIds), FUND_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract().body()
      .as(FundCollection.class);

    assertEquals(3, fundCollection.getFunds().size());

    purge(FUND_TENANT_HEADER);
  }

}
