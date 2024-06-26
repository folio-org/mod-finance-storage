package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.postTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET_EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND_TYPE;
import static org.folio.rest.utils.TestEntities.GROUP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.utils.TenantApiTestUtil;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;


public class TenantSampleDataTest extends TestBase {

  private final Logger logger = LogManager.getLogger(TenantSampleDataTest.class);

  private static final Header ANOTHER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "new_tenant");
  private static final Header MIGRATION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "migration");

  private static TenantJob tenantJob;

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, ANOTHER_TENANT_HEADER);
  }

  @Test
  void sampleDataTests() {
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
  void testLoadSampleDataWithoutUpgrade() {
    logger.info("load sample data");
    try {
      tenantJob = postTenant(ANOTHER_TENANT_HEADER, TenantApiTestUtil.prepareTenantBody(true, true));
      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " + entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
      }
    } finally {
      purge(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  void testLoadReferenceData() {
    logger.info("load only Reference Data");
    try {
      TenantAttributes tenantAttributes = TenantApiTestUtil.prepareTenantBody(false, true);
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

  @Test
  void testMigrationFromVersion2() {
    try {
      prepareTenant(MIGRATION_TENANT_HEADER, false, false);
      var tenantAttributes = TenantApiTestUtil.prepareTenantBody(true, true).withModuleFrom("2.0.0");
      postTenant(MIGRATION_TENANT_HEADER, tenantAttributes);
      TestEntities [] populated = { EXPENSE_CLASS, GROUP };
      for (var entity : populated) {
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), MIGRATION_TENANT_HEADER);
      }
    } finally {
      purge(MIGRATION_TENANT_HEADER);
    }
  }

  @Test
  void testMigrationFromVersion5() {
    try {
      prepareTenant(MIGRATION_TENANT_HEADER, false, false);
      var tenantAttributes = TenantApiTestUtil.prepareTenantBody(true, true).withModuleFrom("5.0.0");
      postTenant(MIGRATION_TENANT_HEADER, tenantAttributes);
      TestEntities [] skipped = { EXPENSE_CLASS, BUDGET_EXPENSE_CLASS };
      for (var entity : skipped) {
        verifyCollectionQuantity(entity.getEndpoint(), 0, MIGRATION_TENANT_HEADER);
      }
      TestEntities [] populated = { FUND_TYPE, FISCAL_YEAR };
      for (var entity : populated) {
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), MIGRATION_TENANT_HEADER);
      }
    } finally {
      purge(MIGRATION_TENANT_HEADER);
    }
  }

  private TenantJob upgradeTenantWithSampleDataLoad() {

    logger.info("upgrading Module with sample");

    TenantJob tenantJob = postTenant(ANOTHER_TENANT_HEADER, TenantApiTestUtil.prepareTenantBody(true, true));
    for (TestEntities entity : TestEntities.values()) {
      logger.info("Test expected quantity for " + entity.name());
      verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
    }
    return tenantJob;
  }

  private TenantJob upgradeTenantWithNoSampleDataLoad() {

    logger.info("upgrading Module without sample data");

    TenantAttributes TenantAttributes = TenantApiTestUtil.prepareTenantBody(false, false);
    return postTenant(ANOTHER_TENANT_HEADER, TenantAttributes);
  }

}
