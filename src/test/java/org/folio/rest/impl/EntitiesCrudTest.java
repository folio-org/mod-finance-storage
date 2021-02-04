package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.BUDGET_EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.FUND_TYPE;
import static org.folio.rest.utils.TestEntities.GROUP;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.LEDGER_FISCAL_YEAR_ROLLOVER;
import static org.folio.rest.utils.TestEntities.LEDGER_FISCAL_YEAR_ROLLOVER_ERROR;
import static org.folio.rest.utils.TestEntities.LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS;
import static org.folio.rest.utils.TestEntities.TRANSACTION;

import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgressCollection;
import org.folio.rest.utils.TestEntities;
import org.junit.Assert;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EntitiesCrudTest extends TestBase {

  private final Logger logger = LogManager.getLogger(EntitiesCrudTest.class);
  private String sample = null;

  /**
   * The Order in which the records must be deleted in order to satisfy the foreign key constraints
   *
   */
  static Stream<TestEntities> deleteOrder() {
    return Stream.of(TRANSACTION, GROUP_FUND_FY, BUDGET_EXPENSE_CLASS, BUDGET, FUND, FUND_TYPE,
      LEDGER_FISCAL_YEAR_ROLLOVER_ERROR, LEDGER_FISCAL_YEAR_ROLLOVER, LEDGER, FISCAL_YEAR,
      GROUP, EXPENSE_CLASS);
  }

  static Stream<TestEntities> deleteFailOrder() {
    return Stream.of(FUND, FUND_TYPE);
  }

  /**
   * Test order of creation of records to fail because of foreign key dependencies
   * @return stream of ordered entities list
   */
  static Stream<TestEntities> createFailOrder() {
    return Stream.of(BUDGET_EXPENSE_CLASS, GROUP_FUND_FY, BUDGET, FUND, LEDGER);
  }

  /**
   * The creation of records must fail because of unique constraint
   * @return stream of ordered entities list
   */
  static Stream<TestEntities> createDuplicateRecords() {
    return Stream.of(BUDGET_EXPENSE_CLASS, BUDGET, GROUP_FUND_FY, FUND, FUND_TYPE, LEDGER, FISCAL_YEAR, GROUP, EXPENSE_CLASS);
  }


  @ParameterizedTest
  @Order(1)
  @EnumSource(TestEntities.class)
  void testVerifyCollection(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Verifying database's initial state ... ", testEntity.name()));
    verifyCollectionQuantity(testEntity.getEndpoint(), 0);
  }

  @ParameterizedTest
  @Order(2)
  @EnumSource(value = TestEntities.class)
  void testPostData(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Creating %s ... ", testEntity.name(), testEntity.name()));
    sample = getSample(testEntity.getSampleFileName());
    Response response = postData(testEntity.getEndpoint(), sample);
    testEntity.setId(response.then()
      .extract()
      .path("id"));
    logger.info(String.format("--- mod-finance-storage %s test: Valid fields exists ... ", testEntity.name()));
    JsonObject sampleJson = convertToMatchingModelJson(sample, testEntity);
    JsonObject responseJson = JsonObject.mapFrom(response.then()
      .extract()
      .response()
      .as(testEntity.getClazz()));
    testAllFieldsExists(responseJson, sampleJson);

    if (testEntity == LEDGER_FISCAL_YEAR_ROLLOVER) {
      LedgerFiscalYearRolloverProgressCollection collection = getData(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS.getEndpoint() + "?query=ledgerRolloverId==" + testEntity.getId() + " AND id<>" + LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS.getId()).as(LedgerFiscalYearRolloverProgressCollection.class);
      String progressId = collection.getLedgerFiscalYearRolloverProgresses().get(0).getId();
      deleteData(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS.getEndpointWithId(), progressId).then()
              .log()
              .ifValidationFails()
              .statusCode(204);
    }
  }

  @ParameterizedTest
  @Order(3)
  @MethodSource("createDuplicateRecords")
  void testPostFailsOnUniqueConstraint(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Creating %s ... fails with unique constraint", testEntity.name(), testEntity.name()));
    JsonObject record = new JsonObject(getSample(testEntity.getSampleFileName()));
    // Make sure that expecting constraint is not PK
    record.remove("id");
    Response response = postData(testEntity.getEndpoint(), record.encodePrettily());
    response.then()
      .log()
      .all()
      .statusCode(400);

    Pattern pattern = Pattern.compile("(already exists|uniqueField)");
    Matcher matcher = pattern.matcher(response.getBody().asString());
    Assert.assertTrue(matcher.find());
  }

  @ParameterizedTest
  @Order(4)
  @EnumSource(TestEntities.class)
  void testVerifyCollectionQuantity(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Verifying only 1 adjustment was created ... ", testEntity.name()));
    verifyCollectionQuantity(testEntity.getEndpoint(), 1);

  }

  @ParameterizedTest
  @Order(5)
  @EnumSource(TestEntities.class)
  void testGetById(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Fetching %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    testEntitySuccessfullyFetched(testEntity.getEndpointWithId(), testEntity.getId());
  }

  @ParameterizedTest
  @Order(6)
  @EnumSource(TestEntities.class)
  void testPutById(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Editing %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    JsonObject catJSON = new JsonObject(getSample(testEntity.getSampleFileName()));
    catJSON.put("id", testEntity.getId());
    catJSON.put(testEntity.getUpdatedFieldName(), testEntity.getUpdatedFieldValue());
    testEntityEdit(testEntity.getEndpointWithId(), catJSON.toString(), testEntity.getId());
  }

  @ParameterizedTest
  @Order(7)
  @EnumSource(TestEntities.class)
  void testVerifyPut(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Fetching updated %s with ID: %s", testEntity.name(),
        testEntity.name(), testEntity.getId()));
    testFetchingUpdatedEntity(testEntity.getId(), testEntity);
  }

  @ParameterizedTest
  @Order(8)
  @MethodSource("deleteFailOrder")
  void testDeleteEndpointForeignKeyFailure(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storages %s test: Deleting %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    deleteData(testEntity.getEndpointWithId(), testEntity.getId()).then()
      .log()
      .ifValidationFails()
      .statusCode(400);
  }

  @ParameterizedTest
  @Order(9)
  @MethodSource("deleteOrder")
  void testDeleteEndpoint(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storages %s test: Deleting %s with ID: %s", testEntity.name(), testEntity.name(),
        testEntity.getId()));
    deleteData(testEntity.getEndpointWithId(), testEntity.getId()).then()
      .log()
      .ifValidationFails()
      .statusCode(204);
  }

  @ParameterizedTest
  @Order(10)
  @EnumSource(TestEntities.class)
  void testVerifyDelete(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storages %s test: Verify %s is deleted with ID: %s", testEntity.name(),
        testEntity.name(), testEntity.getId()));
    testVerifyEntityDeletion(testEntity.getEndpointWithId(), testEntity.getId());
  }

  @ParameterizedTest
  @MethodSource("createFailOrder")
  void testPostFailsOnForeignKeyDependencies(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Creating %s ... fails", testEntity.name(), testEntity.name()));
    sample = getSample(testEntity.getSampleFileName());
    postData(testEntity.getEndpoint(), sample).then().statusCode(400);
  }

  @ParameterizedTest
  @EnumSource(TestEntities.class)
  void testFetchEntityWithNonExistedId(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s get by id test: Invalid %s: %s", testEntity.name(), testEntity.name(),
        NON_EXISTED_ID));
    getDataById(testEntity.getEndpointWithId(), NON_EXISTED_ID).then()
      .log()
      .ifValidationFails()
      .statusCode(404);
  }

  @ParameterizedTest
  @EnumSource(TestEntities.class)
  void testEditEntityWithNonExistedId(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s put by id test: Invalid %s: %s", testEntity.name(), testEntity.name(),
        NON_EXISTED_ID));
    String sampleData = getFile(testEntity.getSampleFileName());
    putData(testEntity.getEndpointWithId(), NON_EXISTED_ID, sampleData).then()
      .log()
      .ifValidationFails()
      .statusCode(404);
  }

  @ParameterizedTest
  @EnumSource(TestEntities.class)
  void testDeleteEntityWithNonExistedId(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s delete by id test: Invalid %s: %s", testEntity.name(), testEntity.name(),
        NON_EXISTED_ID));
    deleteData(testEntity.getEndpointWithId(), NON_EXISTED_ID).then()
      .log()
      .ifValidationFails()
      .statusCode(404);
  }

  @ParameterizedTest
  @EnumSource(TestEntities.class)
  void testGetEntitiesWithInvalidCQLQuery(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- mod-finance-storage %s test: Invalid CQL query", testEntity.name()));
    testInvalidCQLQuery(testEntity.getEndpoint() + "?query=invalid-query");
  }

  private String getSample(String fileName) {
    return getFile(fileName);
  }

}
