package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.junit.Assert.fail;

public class FundCodeUniquenessTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(FundCodeUniquenessTest.class);

  @Test
  public void testFundCodeUniqueness() throws MalformedURLException {

    String sampleId = null;
    try {
       // prepare referenced object
      String ledgerSample = getFile(LEDGER.getPathToSampleFile());
      postData(LEDGER.getEndpoint(), ledgerSample);

      String fundSample = getFile(FUND.getPathToSampleFile());
      sampleId = createEntity(FUND.getEndpoint(), fundSample);

      logger.info("--- mod-finance-storage PO test: Creating fund with the same code ... ");
      JsonObject object = new JsonObject(fundSample);
      object.remove("id");
      Response sameFundCodeResponse = postData(FUND.getEndpoint(), object.toString());
      verifyUniqueness(sameFundCodeResponse);
    } catch (Exception e) {
      logger.error(String.format("--- mod-finance-storage-test: %s API ERROR: %s", FUND.name(), e.getMessage()));
      fail(e.getMessage());
    } finally {
      logger.info(String.format("--- mod-finance-storages %s test: Deleting %s with ID: %s", FUND.name(), FUND.name(), sampleId));
      deleteDataSuccess(FUND.getEndpoint(), sampleId);
    }
  }

  private void verifyUniqueness(Response response) {
    response
      .then()
      .statusCode(400)
      .body(Matchers.containsString("duplicate key value violates unique constraint \"fund_code_idx_unique\""));
  }

}
