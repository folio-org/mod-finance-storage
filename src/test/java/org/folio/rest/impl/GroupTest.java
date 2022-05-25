package org.folio.rest.impl;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.GROUP;
import static org.folio.rest.utils.TestEntities.LEDGER;

public class GroupTest extends TestBase {
  private static final String GROUP_TEST_TENANT = "group_test_tenant";
  private static final Header GROUP_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, GROUP_TEST_TENANT);
  private static TenantJob tenantJob;

  @AfterAll
  static void deleteTable() {
    deleteTenant(tenantJob, GROUP_TENANT_HEADER);
  }

  @Test
  void testUpdateGroupConflict() throws Exception {
    tenantJob = prepareTenant(GROUP_TENANT_HEADER, false, true);

    givenTestData(GROUP_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()),
      Pair.of(GROUP, GROUP.getPathToSampleFile())
    );

    Group group1 = getDataById(GROUP.getEndpointWithId(), GROUP.getId(), GROUP_TENANT_HEADER).as(Group.class);
    Group group2 = getDataById(GROUP.getEndpointWithId(), GROUP.getId(), GROUP_TENANT_HEADER).as(Group.class);

    group2.setDescription("test");

    putData(GROUP.getEndpointWithId(), GROUP.getId(), JsonObject.mapFrom(group1).encodePrettily(), GROUP_TENANT_HEADER);
    putData(GROUP.getEndpointWithId(), GROUP.getId(), JsonObject.mapFrom(group2).encodePrettily(), GROUP_TENANT_HEADER)
      .then()
      .statusCode(409);

    purge(GROUP_TENANT_HEADER);
  }
}
