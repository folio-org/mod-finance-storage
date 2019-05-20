package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.folio.rest.utils.TestEntities.FUND;
import static org.junit.Assert.fail;

public class FundUnTest extends TestBase {

  private final Logger logger = LoggerFactory.getLogger(FundUnTest.class);

  @Test
  public void testFundCodeUniqueness() throws MalformedURLException {

    String sampleId = null;
    try {
      String fundSample = getFile("data/funds/" + FUND.getSampleFileName());

      Response response = postData(FUND.getEndpoint(), fundSample);

      sampleId = response.then().statusCode(201).extract().path("id");

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
      deleteData(FUND.getEndpointWithId(), sampleId, TENANT_HEADER);
    }
  }

  private void verifyUniqueness(Response response) {
    response
      .then()
      .statusCode(400)
      .body(Matchers.containsString("duplicate key value violates unique constraint \"fund_code_unique_idx\""));
  }

}
