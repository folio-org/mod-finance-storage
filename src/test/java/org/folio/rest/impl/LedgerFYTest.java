package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.utils.TestEntities;
import org.junit.Test;

public class LedgerFYTest extends TestBase {

  private static final String LEDGER_FY_TABLE = "ledgerFY";
  private static final String LEDGER_FY_ENDPOINT = "/finance-storage/ledger-fiscal-years";

  @Test
  public void testGetQuery() throws MalformedURLException, InterruptedException {

    String fiscalYearId = testPositiveCases(FISCAL_YEAR);
    String ledgerId = testPositiveCases(LEDGER);

    postLedgerFYData(fiscalYearId, ledgerId);

    // search for GET
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 1);

    // search with fields from "ledger"
    getData(LEDGER_FY_ENDPOINT + "?query=ledger.name==NonExistent").then()
      .log()
      .all()
      .statusCode(200)
      .body("totalRecords", equalTo(0));

 // search with fields from "FY"
    getData(LEDGER_FY_ENDPOINT + "?query=fiscalYear.code==FY19").then()
      .log()
      .all()
      .statusCode(200)
      .body("totalRecords", equalTo(1));

    // search with invalid cql query
    testInvalidCQLQuery(LEDGER_FY_ENDPOINT + "?query=invalid-query");

  }

  // temporary method used to load data to table until MODFISTO-37 is worked on
  private void postLedgerFYData(String fiscalYearId, String ledgerId) {
    String sample = getFile("ledgerFY.sample");
    JsonObject sampleJson = JsonObject.mapFrom(new JsonObject(sample));
    sampleJson.put("ledgerId", ledgerId);
    sampleJson.put("fiscalYearId", fiscalYearId);

    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .save(LEDGER_FY_TABLE, sampleJson, response -> {
        assertThat(response.failed(), equalTo(false));
      });

  }

  private String testPositiveCases(TestEntities testEntity) throws MalformedURLException {
    logger.info(String.format("--- %s test: Verifying database's initial state ... ", testEntity.name()));
    verifyCollectionQuantity(testEntity.getEndpoint(), 0);

    logger.info(String.format("--- %s test: Creating record ... ", testEntity.name()));
    String sample = getFile(testEntity.getPathToSampleFile());
    Response response = postData(testEntity.getEndpoint(), sample);
    String sampleId = response.then()
      .extract()
      .path("id");

    return sampleId;
  }
}
