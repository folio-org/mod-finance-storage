package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.EXCHANGE_RATE_SOURCE;
import static org.hamcrest.Matchers.equalTo;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class ExchangeRateSourceTest extends TestBase {

  private static final String EXCHANGE_RATE_SOURCE_TEST_TENANT = "exchangeratesourcetesttenant";
  private static final Header EXCHANGE_RATE_SOURCE_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, EXCHANGE_RATE_SOURCE_TEST_TENANT);
  private static final String EXCHANGE_RATE_SOURCE_ID = "fef8766e-23b8-4e35-80a7-99ae7e0817fc";

  private static TenantJob tenantJob;

  @BeforeEach
  void prepareData() {
    tenantJob = prepareTenant(EXCHANGE_RATE_SOURCE_TENANT_HEADER, false, true);
  }

  @AfterEach
  void deleteData() {
    purge(EXCHANGE_RATE_SOURCE_TENANT_HEADER);
  }

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, EXCHANGE_RATE_SOURCE_TENANT_HEADER);
  }

  @Test
  void testGetExchangeRateSource() {
    givenTestData(EXCHANGE_RATE_SOURCE_TENANT_HEADER, Pair.of(EXCHANGE_RATE_SOURCE, EXCHANGE_RATE_SOURCE.getPathToSampleFile()));

    getData(EXCHANGE_RATE_SOURCE.getEndpoint(), EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(200)
      .body("id", equalTo(EXCHANGE_RATE_SOURCE_ID));
  }

  @Test
  void testGetExchangeRateSourceNotExists() {
    getData(EXCHANGE_RATE_SOURCE.getEndpoint(), EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(404);
  }

  @Test
  void testSaveExchangeRateSource() {
    String sourceAsString = getFile(EXCHANGE_RATE_SOURCE.getPathToSampleFile());
    postData(EXCHANGE_RATE_SOURCE.getEndpoint(), sourceAsString, EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(201);
  }

  @Test
  void testSaveExchangeRateSourceExists() {
    givenTestData(EXCHANGE_RATE_SOURCE_TENANT_HEADER, Pair.of(EXCHANGE_RATE_SOURCE, EXCHANGE_RATE_SOURCE.getPathToSampleFile()));

    String sourceAsString = getFile(EXCHANGE_RATE_SOURCE.getPathToSampleFile());
    postData(EXCHANGE_RATE_SOURCE.getEndpoint(), sourceAsString, EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(409);
  }

  @Test
  void testUpdateExchangeRateSource() {
    givenTestData(EXCHANGE_RATE_SOURCE_TENANT_HEADER, Pair.of(EXCHANGE_RATE_SOURCE, EXCHANGE_RATE_SOURCE.getPathToSampleFile()));

    String sourceAsString = getFile(EXCHANGE_RATE_SOURCE.getPathToSampleFile());
    putData(EXCHANGE_RATE_SOURCE.getEndpointWithId(), EXCHANGE_RATE_SOURCE_ID, sourceAsString, EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(204);
  }

  @Test
  void testUpdateExchangeRateSourceNotExists() {
    String sourceAsString = getFile(EXCHANGE_RATE_SOURCE.getPathToSampleFile());
    putData(EXCHANGE_RATE_SOURCE.getEndpointWithId(), EXCHANGE_RATE_SOURCE_ID, sourceAsString, EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(404);
  }

  @Test
  void testDeleteExchangeRateSource() {
    givenTestData(EXCHANGE_RATE_SOURCE_TENANT_HEADER, Pair.of(EXCHANGE_RATE_SOURCE, EXCHANGE_RATE_SOURCE.getPathToSampleFile()));

    deleteData(EXCHANGE_RATE_SOURCE.getEndpointWithId(), EXCHANGE_RATE_SOURCE_ID, EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(204);
  }

  @Test
  void tesDeleteExchangeRateSourceNotExists() {
    deleteData(EXCHANGE_RATE_SOURCE.getEndpointWithId(), EXCHANGE_RATE_SOURCE_ID, EXCHANGE_RATE_SOURCE_TENANT_HEADER)
      .then().statusCode(404);
  }

}
