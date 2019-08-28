package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.utils.TenantApiTestUtil;
import org.folio.rest.utils.TestEntities;
import org.junit.Test;

import java.net.MalformedURLException;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TenantApiTestUtil.*;
import static org.folio.rest.utils.TestEntities.BUDGET;


public class TenantSampleDataTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(TenantSampleDataTest.class);

  private static final Header NONEXISTENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "no_tenant");
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
      prepareTenant(ANOTHER_TENANT_HEADER, false);
      logger.info("-- upgrade the tenant with sample data, so that it will be inserted now --");
      upgradeTenantWithSampleDataLoad();
      logger.info("-- upgrade the tenant again with no sample data, so the previously inserted data stays in tact --");
      upgradeTenantWithNoSampleDataLoad();
      upgradeNonExistentTenant();
    } finally {
      deleteTenant(ANOTHER_TENANT_HEADER);
    }
  }

  @Test
  public void failIfNoUrlToHeader() throws MalformedURLException {
    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(true, false);
    given()
      .header(new Header(OKAPI_HEADER_TENANT, "noURL"))
      .contentType(ContentType.JSON)
      .body(jsonBody.encodePrettily())
      .post(storageUrl(TENANT_ENDPOINT))
      .then()
      .assertThat()
      .statusCode(500);
  }

  @Test
  public void testLoadSampleDataWithoutUpgrade() throws MalformedURLException {
    logger.info("load sample data");
    try {
      JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(true, false);
      postToTenant(ANOTHER_TENANT_HEADER_WITHOUT_UPGRADE, jsonBody)
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
  public void testPartialSampleDataLoading() throws MalformedURLException {
    logger.info("load sample data");

    try {
      JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(true, false);
      postToTenant(PARTIAL_TENANT_HEADER, jsonBody)
        .assertThat()
        .statusCode(201);

      Response response = getData(BUDGET.getEndpoint(), PARTIAL_TENANT_HEADER)
        .then()
          .extract()
          .response();

      BudgetCollection budgetCollection = new JsonObject(response.asString()).mapTo(BudgetCollection.class);
      //BudgetCollection budgetCollection = response.as(BudgetCollection.class);

      for (Budget budget : budgetCollection.getBudgets()) {
        deleteData(BUDGET.getEndpointWithId(), budget.getId(), PARTIAL_TENANT_HEADER);
      }

      jsonBody = TenantApiTestUtil.prepareTenantBody(true, true);
      postToTenant(PARTIAL_TENANT_HEADER, jsonBody)
        .assertThat()
        .statusCode(201);

      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " + entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), PARTIAL_TENANT_HEADER);
      }
    } finally {
      deleteTenant(PARTIAL_TENANT_HEADER);
    }
  }


  private void upgradeTenantWithSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module with sample");
    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(true, true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
      .assertThat()
      .statusCode(201);
    for (TestEntities entity : TestEntities.values()) {
      logger.info("Test expected quantity for " + entity.name());
      verifyCollectionQuantity(entity.getEndpoint(), entity.getInitialQuantity(), ANOTHER_TENANT_HEADER);
    }
  }

  private void upgradeTenantWithNoSampleDataLoad() throws MalformedURLException {

    logger.info("upgrading Module without sample data");

    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(false, true);
    postToTenant(ANOTHER_TENANT_HEADER, jsonBody)
      .assertThat()
      .statusCode(200);
  }


  private void upgradeNonExistentTenant() throws MalformedURLException {

    logger.info("upgrading Module for non existed tenant");
    JsonObject jsonBody = TenantApiTestUtil.prepareTenantBody(false, false);
    try {
      // RMB-331 the case if older version has no db schema
      postToTenant(NONEXISTENT_TENANT_HEADER, jsonBody)
        .assertThat()
        .statusCode(201);

      // Check that no sample data loaded
      for (TestEntities entity : TestEntities.values()) {
        logger.info("Test expected quantity for " , 0, entity.name());
        verifyCollectionQuantity(entity.getEndpoint(), 0, NONEXISTENT_TENANT_HEADER);
      }
    }
    finally {
      deleteTenant(NONEXISTENT_TENANT_HEADER);
    }
  }
}
