package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ValidatableResponse;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class FundsTest {
  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  @Before
  public void before(TestContext context) {
    logger.info("--- mod-funds-test: START ");
    vertx = Vertx.vertx();

    // Deploy a verticle
    JsonObject conf = new JsonObject()
      .put(HttpClientMock2.MOCK_MODE, "true")
      .put("http.port", port);
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());

    // Set the default headers for the API calls to be tested
    RestAssured.port = port;
    RestAssured.baseURI = "http://localhost";
  }

  @After
  public void after(TestContext context) {
    async = context.async();
    vertx.close(res -> {   // This logs a stack trace, ignore it.
      logger.info("--- mod-funds-test: END ");
      async.complete();
    });
  }

  // Validates that there are zero vendor records in the DB
  private void emptyCollection() {

    // Validate 200 response and that there are zero records
    getData("budget").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("budgets", empty());

    getData("fiscal_year").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("fiscal_years", empty());

    getData("fund_distribution").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("distributions", empty());

    getData("fund").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("funds", empty());

    getData("ledger").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("ledgers", empty());

    getData("tag").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("tags", empty());

    getData("transaction").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("transactions", empty());
  }

  @Test
  public void testOrders(TestContext context) {
    async = context.async();
    try {
      logger.info("--- mod-funds-test: Verifying empty database ... ");
      emptyCollection();

      logger.info("--- mod-funds-test: Creating tag ... ");
      String tagSample = getFile("tag.sample");
      Response response = postData("tag", tagSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST-SER"));
      String tag_id = response.then().extract().path("id");

      logger.info("--- mod-funds-test: Verifying only 1 tag was created ... ");
      getData("tag").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-funds-test: Fetching tag with ID:"+ tag_id);
      getDataById("tag", tag_id).then()
        .statusCode(200)
        .body("id", equalTo(tag_id));

      logger.info("--- mod-funds-test: Editing tag with ID:"+ tag_id);
      JSONObject tagJSON = new JSONObject(tagSample);
      tagJSON.put("id", tag_id);
      tagJSON.put("code", "PSYCH-SER");
      response = putData("tag", tag_id, tagJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Fetching tag with ID:"+ tag_id);
      getDataById("tag", tag_id).then()
        .statusCode(200)
        .body("code", equalTo("PSYCH-SER"));


      logger.info("--- mod-funds-test: Creating fiscal year ... ");
      String fySample = getFile("fiscal_year.sample");
      response = postData("fiscal_year", fySample);
      response.then()
        .statusCode(201)
        .body("name", equalTo("Fiscal Year 2017"));
      String fy_id = response.then().extract().path("id");

      logger.info("--- mod-funds-test: Verifying only 1 fiscal year was created ... ");
      getData("fiscal_year").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-funds-test: Fetching fiscal year with ID:"+ fy_id);
      getDataById("fiscal_year", fy_id).then()
        .statusCode(200)
        .body("id", equalTo(fy_id));

      logger.info("--- mod-funds-test: Editing fiscal year with ID:"+ fy_id);
      JSONObject fyJSON = new JSONObject(fySample);
      fyJSON.put("id", fy_id);
      fyJSON.put("name", "Fiscal Year 2017 B");
      response = putData("fiscal_year", fy_id, fyJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Fetching fiscal year with ID:"+ fy_id);
      getDataById("fiscal_year", fy_id).then()
        .statusCode(200)
        .body("name", equalTo("Fiscal Year 2017 B"));


      logger.info("--- mod-funds-test: Creating ledger ... ");
      String ledgerSample = getFile("ledger.sample");
      response = postData("ledger", ledgerSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("MAIN-LIB"));
      String ledger_id = response.then().extract().path("id");

      logger.info("--- mod-funds-test: Verifying only 1 ledger was created ... ");
      getData("ledger").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-funds-test: Fetching ledger with ID:"+ ledger_id);
      getDataById("ledger", ledger_id).then()
        .statusCode(200)
        .body("id", equalTo(ledger_id));

      logger.info("--- mod-funds-test: Editing ledger with ID:"+ ledger_id);
      JSONObject ledgerJSON = new JSONObject(ledgerSample);
      ledgerJSON.put("id", ledger_id);
      ledgerJSON.put("code", "MAIN-LIB-B");
      response = putData("ledger", ledger_id, ledgerJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Fetching ledger with ID:"+ ledger_id);
      getDataById("ledger", ledger_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-B"));


      logger.info("--- mod-funds-test: Creating fund ... ");
      String fundSample = getFile("fund.sample");
      response = postData("fund", fundSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST"));
      String fund_id = response.then().extract().path("id");

      logger.info("--- mod-funds-test: Verifying only 1 fund was created ... ");
      getData("fund").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-funds-test: Fetching fund with ID:"+ fund_id);
      getDataById("fund", fund_id).then()
        .statusCode(200)
        .body("id", equalTo(fund_id));

      logger.info("--- mod-funds-test: Editing fund with ID:"+ fund_id);
      JSONObject fundJSON = new JSONObject(fundSample);
      fundJSON.put("id", fund_id);
      fundJSON.put("code", "MAIN-LIB-FUND");
      fundJSON.put("ledger_id", ledger_id);
      JSONArray tagArray = fundJSON.getJSONArray("tags");
      tagArray.put(tag_id);
      fundJSON.put("tags", tagArray);
      response = putData("fund", fund_id, fundJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Fetching fund with ID:"+ fund_id);
      getDataById("fund", fund_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-FUND"));


      logger.info("--- mod-funds-test: Creating budget ... ");
      String budgetSample = getFile("budget.sample");
      JSONObject budgetJSON = new JSONObject(budgetSample);
      budgetJSON.put("fund_id", fund_id);
      budgetJSON.put("fiscal_year_id", fy_id);
      tagArray = budgetJSON.getJSONArray("tags");
      tagArray.put(tag_id);
      budgetJSON.put("tags", tagArray);
      response = postData("budget", budgetJSON.toString());
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST-2017"));
      String budget_id = response.then().extract().path("id");
      budgetJSON.put("id", budget_id);

      logger.info("--- mod-funds-test: Verifying only 1 budget was created ... ");
      getData("budget").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-funds-test: Fetching budget with ID:"+ budget_id);
      getDataById("budget", budget_id).then()
        .statusCode(200)
        .body("id", equalTo(budget_id));

      logger.info("--- mod-funds-test: Editing fund with ID:"+ budget_id);
      budgetJSON.put("code", "MAIN-LIB-HIST-2017");
      response = putData("budget", budget_id, budgetJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Fetching budget with ID:"+ budget_id);
      getDataById("budget", budget_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-HIST-2017"));


      logger.info("--- mod-funds-test: Creating transaction ... ");
      String transactionSample = getFile("transaction.sample");
      JSONObject transactionJSON = new JSONObject(transactionSample);
      transactionJSON.put("budget_id", budget_id);
      response = postData("transaction", transactionJSON.toString());
      response.then()
        .statusCode(201)
        .body("note", equalTo("PO_Line: History of Incas"));
      String transaction_id = response.then().extract().path("id");
      transactionJSON.put("id", transaction_id);

      logger.info("--- mod-funds-test: Verifying only 1 transaction was created ... ");
      getData("transaction").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-funds-test: Fetching transaction with ID:"+ transaction_id);
      getDataById("transaction", transaction_id).then()
        .statusCode(200)
        .body("id", equalTo(transaction_id));

      logger.info("--- mod-funds-test: Editing transaction with ID:"+ transaction_id);
      transactionJSON.put("note", "PO_Line: The History of Incas");
      response = putData("transaction", transaction_id, transactionJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Fetching transaction with ID:"+ transaction_id);
      getDataById("transaction", transaction_id).then()
        .statusCode(200)
        .body("note", equalTo("PO_Line: The History of Incas"));


      logger.info("--- mod-funds-test: Creating fund-distribution ... ");
      String distributionSample = getFile("fund_distribution.sample");
      JSONObject distributionJSON = new JSONObject(distributionSample);
      distributionJSON.put("budget_id", budget_id);
      response = postData("fund_distribution", distributionJSON.toString());
      response.then()
        .statusCode(201)
        .body("currency", equalTo("USD"));
      String distribution_id = response.then().extract().path("id");
      distributionJSON.put("id", distribution_id);

      logger.info("--- mod-funds-test: Verifying only 1 fund-distribution was created ... ");
      getData("fund_distribution").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-funds-test: Fetching fund-distribution with ID:"+ distribution_id);
      getDataById("fund_distribution", distribution_id).then()
        .statusCode(200)
        .body("id", equalTo(distribution_id));

      logger.info("--- mod-funds-test: Editing fund-distribution with ID:"+ distribution_id);
      distributionJSON.put("currency", "CAD");
      response = putData("fund_distribution", distribution_id, distributionJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Fetching fund-distribution with ID:"+ distribution_id);
      getDataById("fund_distribution", distribution_id).then()
        .statusCode(200)
        .body("currency", equalTo("CAD"));


      logger.info("--- mod-funds-test: Deleting fund-distribution ... ");
      deleteData("fund_distribution", distribution_id).then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Deleting transaction ... ");
      deleteData("transaction", transaction_id).then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Deleting budget ... ");
      deleteData("budget", budget_id).then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Deleting fund ... ");
      deleteData("fund", fund_id).then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Deleting ledger ... ");
      deleteData("ledger", ledger_id).then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Deleting fiscal_year ... ");
      deleteData("fiscal_year", fy_id).then()
        .statusCode(204);

      logger.info("--- mod-funds-test: Deleting tag ... ");
      deleteData("tag", tag_id).then()
        .statusCode(204);
    }
    catch (Exception e) {
      context.fail("--- mod-funds-test: ERROR: " + e.getMessage());
    }
    async.complete();
  }

  private String getFile(String filename) {
    String value;
    try {
      InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filename);
      value = IOUtils.toString(inputStream, "UTF-8");
    } catch (Exception e) {
      value = "";
    }
    return value;
  }

  private Response getData(String endpoint) {
    return given()
      .header("X-Okapi-Tenant","diku")
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

  private Response getDataById(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant","diku")
      .contentType(ContentType.JSON)
      .get(endpoint + "/{id}");
  }

  private Response postData(String endpoint, String input) {
    return given()
      .header("X-Okapi-Tenant", "diku")
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(endpoint);
  }

  private Response putData(String endpoint, String id, String input) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", "diku")
      .contentType(ContentType.JSON)
      .body(input)
      .put(endpoint + "/{id}");
  }

  private Response deleteData(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", "diku")
      .contentType(ContentType.JSON)
      .delete(endpoint + "/{id}");
  }
}
