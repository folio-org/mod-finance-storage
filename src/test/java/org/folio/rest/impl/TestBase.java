package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.StorageTestSuite;
import org.folio.rest.utils.TestEntities;
import org.folio.utils.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {
  protected final Logger logger = LogManager.getLogger(this.getClass());

  protected static final String TENANT_NAME = "diku";
  static final String NON_EXISTED_ID = "bad500aa-aaaa-500a-aaaa-aaaaaaaaaaaa";
  public static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  private static boolean invokeStorageTestSuiteAfter = false;

  @BeforeAll
  public static void testBaseBeforeClass() throws Exception {
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
    TimeoutException {

    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }

  @SafeVarargs
  final void givenTestData(Header header, Pair<TestEntities, String> ... testPairs) {
    for(Pair<TestEntities, String> pair: testPairs) {

      String sample = getFile(pair.getRight());
      String id = new JsonObject(sample).getString("id");
      pair.getLeft().setId(id);

      postData(pair.getLeft().getEndpoint(), sample, header)
        .then()
        .statusCode(201);
    }
  }

  ValidatableResponse verifyCollectionQuantity(String endpoint, int quantity, Header tenantHeader) {
    return getData(endpoint, tenantHeader)
      .then()
        .log().all()
        .statusCode(200)
        .body("totalRecords", equalTo(quantity));
  }

  ValidatableResponse verifyCollectionQuantity(String endpoint, int quantity) {
    return verifyCollectionQuantity(endpoint, quantity, TENANT_HEADER);
  }

  Response getData(String endpoint, Header tenantHeader) {
    return given()
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getData(String endpoint) {
    return getData(endpoint, TENANT_HEADER);
  }

  public static String getFile(String filename) {
    String value;
    try {
      InputStream inputStream = TestBase.class.getClassLoader().getResourceAsStream(filename);
      value = IOUtils.toString(inputStream, "UTF-8");
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  Response postData(String endpoint, String input) {
    return given()
      .header(TENANT_HEADER)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .log().all()
      .post(storageUrl(endpoint));
  }

  Response postData(String endpoint, String input, Header header) {
    return given()
      .header(header)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .log().all()
      .post(storageUrl(endpoint));
  }

  Response getDataById(String endpoint, String id) {
    return getDataById(endpoint, id, TENANT_HEADER);
  }

  Response getDataById(String endpoint, String id, Header header) {
    return given()
      .pathParam("id", id)
      .header(header)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response putData(String endpoint, String input, Header tenant) {
    return given()
      .header(tenant)
      .contentType(ContentType.JSON)
      .body(input)
      .put(storageUrl(endpoint));
  }

  Response putData(String endpoint, String id, String input, Header tenant) {
    return given()
      .pathParam("id", id)
      .header(tenant)
      .contentType(ContentType.JSON)
      .body(input)
      .put(storageUrl(endpoint));
  }

  Response putData(String endpoint, String id, String input) {
    return putData(endpoint, id, input, TENANT_HEADER);
  }

  void deleteDataSuccess(String endpoint, String id) {
    deleteData(endpoint, id)
      .then().log().ifValidationFails()
      .statusCode(204);
  }

  Response deleteData(String endpoint, String id) {
    return deleteData(endpoint, id, TENANT_HEADER);
  }

  Response deleteData(String endpoint, String id, Header tenantHeader) {
    return given()
      .pathParam("id", id)
      .header(tenantHeader)
      .contentType(ContentType.JSON)
      .delete(storageUrl(endpoint));
  }

  String createEntity(String endpoint, Object entity, Header tenantHeader) {
    return postData(endpoint, valueAsString(entity), tenantHeader)
      .then().log().all()
      .statusCode(201)
      .extract()
      .path("id");
  }

  String createEntity(String endpoint, Object entity) {
    return postData(endpoint, valueAsString(entity))
      .then().log().all()
      .statusCode(201)
      .extract()
      .path("id");
  }

  JsonObject convertToMatchingModelJson(String sample, TestEntities testEntity) {
    return JsonObject.mapFrom(new JsonObject(sample).mapTo(testEntity.getClazz()));
  }

  static String valueAsString(Object o) {
    return ObjectMapper.valueAsString(o);
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

  void testInvalidCQLQuery(String endpoint) {
    getData(endpoint).then().log().ifValidationFails()
      .statusCode(400);
  }

  void testEntityEdit(String endpoint, String entitySample, String id) {
    putData(endpoint, id, entitySample)
      .then().log().ifValidationFails()
      .statusCode(204);
  }

  void testFetchingUpdatedEntity(String id, TestEntities subObject) {
    Object prop = getDataById(subObject.getEndpointWithId(), id).then()
      .log().ifValidationFails()
      .statusCode(200)
      .extract()
      .path(subObject.getUpdatedFieldName());

    // Get string value of updated field and compare
    assertEquals(String.valueOf(prop), subObject.getUpdatedFieldValue());
  }

  Response testEntitySuccessfullyFetched(String endpoint, String id) {
    Response response = getDataById(endpoint, id);
    response.then()
      .log().ifValidationFails()
      .statusCode(200)
      .body("$", either(hasEntry(equalTo("id"), equalTo(id)))
        .or(hasEntry(equalTo("ledgerRolloverId"), equalTo(id))));
    return response;
  }


  void testVerifyEntityDeletion(String endpoint, String id) {
    getDataById(endpoint, id)
      .then()
        .statusCode(404);
  }

}
