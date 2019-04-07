package org.folio.rest.impl;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.folio.rest.utils.TestEntities;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static io.restassured.RestAssured.given;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

/**
 * When not run from StorageTestSuite then this class invokes StorageTestSuite.before() and
 * StorageTestSuite.after() to allow to run a single test class, for example from within an
 * IDE during development.
 */
public abstract class TestBase {

  static final String NON_EXISTED_ID = "bad500aa-aaaa-500a-aaaa-aaaaaaaaaaaa";
  private static final String TENANT_NAME = "diku";
  static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);

  private static boolean invokeStorageTestSuiteAfter = false;

  @BeforeClass
  public static void testBaseBeforeClass() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    Vertx vertx = StorageTestSuite.getVertx();
    if (vertx == null) {
      invokeStorageTestSuiteAfter = true;
      StorageTestSuite.before();
    }

  }

  @AfterClass
  public static void testBaseAfterClass()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    if (invokeStorageTestSuiteAfter) {
      StorageTestSuite.after();
    }
  }

  void verifyCollectionQuantity(String endpoint, int quantity, Header tenantHeader) throws MalformedURLException {
    getData(endpoint, tenantHeader)
      .then()
      .log().all()
      .statusCode(200)
      .body("total_records", equalTo(quantity));
  }


  void verifyCollectionQuantity(String endpoint, int quantity) throws MalformedURLException {
    // Verify that there are no existing  records
    verifyCollectionQuantity(endpoint, quantity,TENANT_HEADER);
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

  void testVerifyEntityDeletion(String endpoint, String id) throws MalformedURLException {
    getDataById(endpoint, id)
      .then()
      .statusCode(404);
  }

  Response getDataById(String endpoint, String id) throws MalformedURLException {
    return given()
      .pathParam("id", id)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response getDataByParam(String endpoint, Map<String, Object> params) throws MalformedURLException {
    return given()
      .params(params)
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(endpoint));
  }

  Response postData(String endpoint, String input) throws MalformedURLException {
    return given()
      .header(TENANT_HEADER)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(storageUrl(endpoint));
  }

  String createEntity(String endpoint, String entity) throws MalformedURLException {
    return postData(endpoint, entity)
      .then().log().ifValidationFails()
      .statusCode(201)
      .extract()
      .path("id");
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


  void testEntityEdit(String endpoint, String entitySample, String id) throws MalformedURLException {
    putData(endpoint, id, entitySample)
      .then().log().ifValidationFails()
      .statusCode(204);
  }

  void testFetchingUpdatedEntity(String id, TestEntities subObject) throws MalformedURLException {
    String existedValue = getDataById(subObject.getEndpointWithId(), id)
      .then()
      .statusCode(200).log().ifValidationFails()
      .extract()
      .body()
      .jsonPath()
      .get(subObject.getUpdatedFieldName()).toString();
    assertEquals(existedValue, subObject.getUpdatedFieldValue());
  }

  void testInvalidCQLQuery(String endpoint) throws MalformedURLException {
    getData(endpoint).then().log().ifValidationFails()
      .statusCode(400);
  }

  void testEntitySuccessfullyFetched(String endpoint, String id) throws MalformedURLException {
    getDataById(endpoint, id)
      .then().log().ifValidationFails()
      .statusCode(200)
      .body("id", equalTo(id));
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

  String getFile(String filename) {
    String value = "";
    try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename)) {
      if (inputStream != null) {
        value = IOUtils.toString(inputStream, "UTF-8");
      }
    } catch (Exception e) {
      value = "";
    }
    return value;
  }


}
