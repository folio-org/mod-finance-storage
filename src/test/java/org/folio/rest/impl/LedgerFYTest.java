package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.LEDGER;

import java.net.MalformedURLException;

import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.response.Response;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class LedgerFYTest extends TestBase {

  private static final String LEDGER_FY_TABLE = "ledgerFY";
  private static final String LEDGER_FY_ENDPOINT = "/finance-storage/ledger-fiscal-years";

  @Test
  public void testGetQuery(VertxTestContext testContext) throws Exception {

    String fiscalYearId = testPositiveCases(FISCAL_YEAR);
    String ledgerId = testPositiveCases(LEDGER);
    Checkpoint checkpoint = testContext.checkpoint(2);

    Handler<AsyncResult<String>> postHandler = testContext.succeeding(ok -> checkpoint.flag());
    postLedgerFYData(fiscalYearId, ledgerId, postHandler);

    // search for GET
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 1);

    // search with fields from "ledger"
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=ledger.name==NonExistent", 0);

 // search with fields from "FY"
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=fiscalYear.code==FY19", 1);

    // search with invalid cql query
    testInvalidCQLQuery(LEDGER_FY_ENDPOINT + "?query=invalid-query");

    deleteLedgerFYData(testContext.succeeding(ok -> checkpoint.flag()));
    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), fiscalYearId);
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
  }

  private void deleteLedgerFYData(Handler<AsyncResult<UpdateResult>> handler) {
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
    .delete(LEDGER_FY_TABLE, new CQLWrapper(), handler);
  }

  // temporary method used to load data to table until MODFISTO-37 is worked on
  private void postLedgerFYData(String fiscalYearId, String ledgerId, Handler<AsyncResult<String>> handler) {
    String sample = getFile("ledgerFY.sample");
    JsonObject sampleJson = JsonObject.mapFrom(new JsonObject(sample));
    sampleJson.put("ledgerId", ledgerId);
    sampleJson.put("fiscalYearId", fiscalYearId);

    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .save(LEDGER_FY_TABLE, sampleJson, handler);

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
