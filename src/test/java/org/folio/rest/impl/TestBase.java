package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.apache.commons.io.IOUtils;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import javax.ws.rs.Path;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  static final String TENANT_NAME = "diku";
  static final String NON_EXISTED_ID = "bad500aa-aaaa-500a-aaaa-aaaaaaaaaaaa";
  static final String FISCAL_YEAR_ONE_ID = "d875618b-7cf5-442f-a823-9814e7bce92a";
  static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  private static boolean invokeStorageTestSuiteAfter = false;

  @BeforeAll
  public static void testBaseBeforeClass() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
    }

  }

  @AfterAll
  public static void testBaseAfterClass()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }

  ValidatableResponse verifyCollectionQuantity(String endpoint, int quantity, Header tenantHeader) throws MalformedURLException {
    return getData(endpoint, tenantHeader)
      .then()
        .log().all()
        .statusCode(200)
        .body("totalRecords", equalTo(quantity));
  }

  ValidatableResponse verifyCollectionQuantity(String endpoint, int quantity) throws MalformedURLException {
    return verifyCollectionQuantity(endpoint, quantity, TENANT_HEADER);
  }

  Response getData(String endpoint, Header tenantHeader) throws MalformedURLException {
    return given()
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getData(String endpoint) throws MalformedURLException {
    return getData(endpoint, TENANT_HEADER);
  }

  String getFile(String filename) {
    String value;
    try {
      InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename);
      value = IOUtils.toString(inputStream, "UTF-8");
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  Response postData(String endpoint, String input) throws MalformedURLException {
    return given()
      .header(TENANT_HEADER)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .log().all()
      .post(storageUrl(endpoint));
  }

  Response postData(String endpoint, String input, Header header) throws MalformedURLException {
    return given()
      .header(header)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .log().all()
      .post(storageUrl(endpoint));
  }

  Response getDataById(String endpoint, String id) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getDataById(String endpoint, String id, Header header) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(header)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response putData(String endpoint, String id, String input) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .body(input)
      .put(storageUrl(endpoint));
  }

  void deleteDataSuccess(String endpoint, String id) throws MalformedURLException {
    deleteData(endpoint, id)
      .then().log().ifValidationFails()
      .statusCode(204);
  }

  void deleteDataSuccess(TestEntities testEntity, String id) throws MalformedURLException {
    logger.info(String.format("--- %s test: Deleting record with ID: %s", testEntity.name(), id));
    deleteData(testEntity.getEndpointWithId(), id)
      .then().log().ifValidationFails()
      .statusCode(204);
  }

  Response deleteData(String endpoint, String id) throws MalformedURLException {
    return deleteData(endpoint, id, TENANT_HEADER);
  }

  Response deleteData(String endpoint, String id, Header tenantHeader) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .delete(storageUrl(endpoint));
  }

  String createEntity(String endpoint, Object entity) throws MalformedURLException {
    return postData(endpoint, JsonObject.mapFrom(entity).encode())
      .then().log().all()
      .statusCode(201)
      .extract()
      .path("id");
  }

  JsonObject convertToMatchingModelJson(String sample, TestEntities testEntity) {
    return JsonObject.mapFrom(new JsonObject(sample).mapTo(testEntity.getClazz()));
  }

  void testAllFieldsExists(JsonObject extracted, JsonObject sampleObject) {
    Set<String> fieldsNames = sampleObject.fieldNames();
    for (String fieldName : fieldsNames) {
      Object sampleField = sampleObject.getValue(fieldName);
      if (sampleField instanceof JsonObject) {
        testAllFieldsExists((JsonObject) sampleField, (JsonObject) extracted.getValue(fieldName));
      } else {
        assertEquals(sampleObject.getValue(fieldName).toString(), extracted.getValue(fieldName).toString());
      }

    }
  }

  void testInvalidCQLQuery(String endpoint) throws MalformedURLException {
    getData(endpoint).then().log().ifValidationFails()
      .statusCode(400);
  }
  void testEntityEdit(String endpoint, String entitySample, String id) throws MalformedURLException {
    putData(endpoint, id, entitySample)
      .then().log().ifValidationFails()
      .statusCode(204);
  }

  void testFetchingUpdatedEntity(String id, TestEntities subObject) throws MalformedURLException {
    Object prop = getDataById(subObject.getEndpointWithId(), id).then()
      .log().ifValidationFails()
      .statusCode(200)
      .extract()
      .path(subObject.getUpdatedFieldName());

    // Get string value of updated field and compare
    assertThat(String.valueOf(prop), equalTo(subObject.getUpdatedFieldValue()));
  }

  Response testEntitySuccessfullyFetched(String endpoint, String id) throws MalformedURLException {
    Response response = getDataById(endpoint, id);
    response.then()
      .log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(id));
    return response;
  }


  void testVerifyEntityDeletion(String endpoint, String id) throws MalformedURLException {
    getDataById(endpoint, id)
      .then()
        .statusCode(404);
  }


  static String getUriPath(Class<?> clazz) {
    return clazz.getAnnotation(Path.class).value();
  }
}
