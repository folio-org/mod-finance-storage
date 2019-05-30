package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

import org.folio.rest.utils.TestEntities;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.ENCUMBRANCE;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.FUND_DISTRIBUTION;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.folio.rest.utils.TestEntities.TRANSACTION;

public class FundsTest extends TestBase {

  @Test
  public void testStorageEndpoints() throws MalformedURLException {
    // Order is important
    String fiscalYearId = testPositiveCases(FISCAL_YEAR);
    String ledgerId = testPositiveCases(LEDGER);
    String fundId = testPositiveCases(FUND);
    String budgetId = testPositiveCases(BUDGET);
    String transactionId = testPositiveCases(TRANSACTION);
    String fundDistributionId = testPositiveCases(FUND_DISTRIBUTION);
    String encumbranceId = testPositiveCases(ENCUMBRANCE);

    deleteDataSuccess(ENCUMBRANCE, encumbranceId);
    deleteDataSuccess(FUND_DISTRIBUTION, fundDistributionId);
    deleteDataSuccess(TRANSACTION, transactionId);
    deleteDataSuccess(BUDGET, budgetId);
    deleteDataSuccess(FUND, fundId);
    deleteDataSuccess(LEDGER, ledgerId);
    deleteDataSuccess(FISCAL_YEAR, fiscalYearId);
  }

  private String testPositiveCases(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- %s test: Verifying database's initial state ... ", testEntity.name()));
    verifyCollectionQuantity(testEntity.getEndpoint(), 0);

    logger.info(String.format("--- %s test: Creating record ... ", testEntity.name()));
    String sample = getFile(testEntity.getPathToSampleFile());
    JsonObject sampleJson = convertToMatchingModelJson(sample, testEntity);
    Response response = postData(testEntity.getEndpoint(), sample);
    String sampleId = response.then().extract().path("id");

    logger.info(String.format("--- %s test: Valid fields exists ... ", testEntity.name()));
    JsonObject responseJson = convertToMatchingModelJson(response.asString(), testEntity);
    testAllFieldsExists(responseJson, sampleJson);

    logger.info(String.format("--- %s test: Verifying only 1 record was created ... ", testEntity.name()));
    verifyCollectionQuantity(testEntity.getEndpoint(),1);

    logger.info(String.format("--- %s test: Fetching record with ID: %s", testEntity.name(), sampleId));
    response = testEntitySuccessfullyFetched(testEntity.getEndpoint(), sampleId);
    responseJson = convertToMatchingModelJson(response.asString(), testEntity);
    testAllFieldsExists(responseJson, sampleJson);

    logger.info(String.format("--- %s test: Editing record with ID: %s", testEntity.name(), sampleId));
    JsonObject catJSON = new JsonObject(sample);
    catJSON.put(testEntity.getUpdatedFieldName(), testEntity.getUpdatedFieldValue());
    testEntityEdit(testEntity.getEndpoint(), catJSON.toString(), sampleId);

    logger.info(String.format("--- %s test: Fetching updated record with ID: %s", testEntity.name(), sampleId));
    testFetchingUpdatedEntity(sampleId, testEntity);

    return sampleId;
  }
}
