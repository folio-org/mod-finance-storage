package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TenantApiTestUtil.TENANT_ENDPOINT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.postToTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenantBody;
import static org.folio.rest.utils.TestEntities.FUND_TYPE;

import java.net.MalformedURLException;

import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class TenantSampleDataTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(TenantSampleDataTest.class);

  private static final Header ANOTHER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "new_tenant");
  private static final Header ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE = new Header(OKAPI_HEADER_TENANT, "no_upgrade_tenant");
  private static final Header PARTIAL_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "partial_tenant");


  @Test
  public void isTenantCreated() throws MalformedURLException {
    getData(TENANT_ENDPOINT)
      .then()
      .assertThat()
      .statusCode(200);
  }

  @Test
  public void sampleDataTests() throws MalformedURLException {
    try {
      logger.info("-- create a tenant with no sample data --");
      prepareTenant(ANOTHER_TENANT_HEADER, false,false);
      logger.info("-- upgrade the tenant with sample data, so that it will be inserted now --");
      upgradeTenantWithSampleDataLoad();
      logger.info("-- upgrade the tenant again with no sample data, so the previously inserted data stays in tact --");
      upgradeTenantWithNoSampleDataLoad();
    } finally {
      deleteTenant(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  public void failIfNoUrlToHeader() throws MalformedURLException {
    given()
      .header(new Header(OKAPI_HEADER_TENANT, "noURL"))
      .contentType(ContentType.JSON)
      .body(prepareTenantBody().encodePrettily())
      .post(storageUrl(TENANT_ENDPOINT))
      .then()
      .assertThat()
      .statusCode(500);
  }

  @Test
  public void testLoadSampleDataWithoutUpgrade() throws MalformedURLException {
    logger.info("load sample data");
    try {
      postToTenant(ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE, prepareTenantBody())
        .assertThat()
        .statusCode(201);
      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " + entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE);
      }
    } finally {
      deleteTenant(ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE);
    }
  }


  @Test
  public void testLoadReferenceData() throws MalformedURLException {
    logger.info("load only Reference Data");
    try {
      JsonObject jsonBody = prepareTenantBody(false, true);
      postToTenant(PARTIAL_TENANT_HEADER, jsonBody)
        .assertThat()
        .statusCode(201);
      verifyCollectionQuantity(FUND_TYPE.getEndpoint(), FUND_TYPE.getInitialQuantity(), PARTIAL_TENANT_HEADER);
      for (TestEntities entity : TestEntities.values()) {
        //category is the only reference data, which must be loaded
        if (!entity.equals(TestEntities.FUND_TYPE)) {
          logger.info("Test sample data not loaded for " + entity.name());
          verifyCollectionQuantity(entity.getEndpoint(), 0, PARTIAL_TENANT_HEADER);
        }
      }
    } finally {
      deleteTenant(PARTIAL_TENANT_HEADER);
    }
  }


  private void upgradeTenantWithSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module with sample");

    postToTenant(ANOTHER_TENANT_HEADER, prepareTenantBody())
      .assertThat()
      .statusCode(201);
    for (TestEntities entity : TestEntities.values()) {
      logger.info("Test expected quantity for " + entity.name());
      verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
    }
  }

  private void upgradeTenantWithNoSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module without sample data");

    JsonObject jsonBody = prepareTenantBody(false, false);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
      .assertThat()
      .statusCode(200);
  }
}
