package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.postTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenantBody;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.FUND_TYPE;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;


public class TenantSampleDataTest extends TestBase {

  private final Logger logger = LogManager.getLogger(TenantSampleDataTest.class);

  private static final Header ANOTHER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "new_tenant");

  private static TenantJob tenantJob;

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, ANOTHER_TENANT_HEADER);
  }

  @Test
  void sampleDataTests() throws Exception {
    try {
      logger.info("-- create a tenant with no sample data --");
      tenantJob = prepareTenant(ANOTHER_TENANT_HEADER, false,false);
      logger.info("-- upgrade the tenant with sample data, so that it will be inserted now --");
      tenantJob = upgradeTenantWithSampleDataLoad();
      logger.info("-- upgrade the tenant again with no sample data, so the previously inserted data stays in tact --");
      tenantJob = upgradeTenantWithNoSampleDataLoad();
    } finally {
      purge(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  void testLoadSampleDataWithoutUpgrade() throws Exception {
    logger.info("load sample data");
    try {
      tenantJob = postTenant(ANOTHER_TENANT_HEADER, prepareTenantBody());
      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " + entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
      }
    } finally {
      purge(ANOTHER_TENANT_HEADER);
    }
  }


  @Test
  void testLoadReferenceData() throws Exception {
    logger.info("load only Reference Data");
    try {
      TenantAttributes tenantAttributes = prepareTenantBody(false, true);
      tenantJob = postTenant(ANOTHER_TENANT_HEADER, tenantAttributes);
      verifyCollectionQuantity(FUND_TYPE.getEndpoint(), FUND_TYPE.getInitialQuantity(), ANOTHER_TENANT_HEADER);
      verifyCollectionQuantity(EXPENSE_CLASS.getEndpoint(), EXPENSE_CLASS.getInitialQuantity(), ANOTHER_TENANT_HEADER);

      for (TestEntities entity : TestEntities.values()) {
        //category is the only reference data, which must be loaded
        if (!entity.equals(TestEntities.FUND_TYPE) && !entity.equals(TestEntities.EXPENSE_CLASS)) {
          logger.info("Test sample data not loaded for " + entity.name());
          verifyCollectionQuantity(entity.getEndpoint(), 0, ANOTHER_TENANT_HEADER);
        }
      }
    } finally {
        purge(ANOTHER_TENANT_HEADER);
    }

  }



  private TenantJob upgradeTenantWithSampleDataLoad() throws Exception {

    logger.info("upgrading Module with sample");

    TenantJob tenantJob = postTenant(ANOTHER_TENANT_HEADER, prepareUpgradeTenantBody());
    for (TestEntities entity : TestEntities.values()) {
      logger.info("Test expected quantity for " + entity.name());
      verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
    }
    return tenantJob;
  }

  private TenantJob upgradeTenantWithNoSampleDataLoad() {

    logger.info("upgrading Module without sample data");

    TenantAttributes TenantAttributes = prepareUpgradeTenantBody(false, false);
    return postTenant(ANOTHER_TENANT_HEADER, TenantAttributes);
  }

  private TenantAttributes prepareUpgradeTenantBody() {
    return prepareUpgradeTenantBody(true, true);
  }

  private TenantAttributes prepareUpgradeTenantBody(boolean isLoadSampleData, boolean isLoadReferenceData) {
    String moduleName = ModuleName.getModuleName();

    List<Parameter> parameters = new ArrayList<>();
    parameters.add(new Parameter().withKey("loadReference").withValue(String.valueOf(isLoadReferenceData)));
    parameters.add(new Parameter().withKey("loadSample").withValue(String.valueOf(isLoadSampleData)));

    return new TenantAttributes()
            .withModuleTo(moduleName + "-5.0.1")
            .withModuleFrom(moduleName + "-5.0.0")
            .withParameters(parameters);
  }
}
