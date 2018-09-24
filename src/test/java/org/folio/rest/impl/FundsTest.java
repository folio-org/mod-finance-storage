package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
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
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class FundsTest {
  private Vertx vertx;
  private Async async;
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));

  private final String TENANT_NAME = "testlib";
  private final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);

  private String moduleName;      // "mod_finance_storage";
  private String moduleVersion;   // "1.0.0"
  private String moduleId;        // "mod-finance-storage-1.0.0"

  @Before
  public void before(TestContext context) {
    logger.info("--- mod-finance-test: START ");
    vertx = Vertx.vertx();

    moduleName = PomReader.INSTANCE.getModuleName();
    moduleVersion = PomReader.INSTANCE.getVersion();

    moduleId = String.format("%s-%s", moduleName, moduleVersion);

    // RMB returns a 'normalized' name, with underscores
    moduleId = moduleId.replaceAll("_", "-");

    try {
      // Run this test in embedded postgres mode
      // IMPORTANT: Later we will initialize the schema by calling the tenant interface.
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
      PostgresClient.getInstance(vertx).dropCreateDatabase(TENANT_NAME + "_" + PomReader.INSTANCE.getModuleName());

    } catch (Exception e) {
      e.printStackTrace();
      context.fail(e);
      return;
    }

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
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
      logger.info("--- mod-finance-test: END ");
    });
  }

  // Validates that there are zero vendor records in the DB
  private void verifyInitialDBState() {

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

    getData("transaction").then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("transactions", empty());
  }

  @Test
  public void testOrders(TestContext context) {
    async = context.async();
    try {
      // IMPORTANT: Call the tenant interface to initialize the tenant-schema
      logger.info("--- mod-vendors-test: Preparing test tenant");
      prepareTenant();

      logger.info("--- mod-finance-test: Verifying empty database ... ");
      verifyInitialDBState();

      logger.info("--- mod-finance-test: Creating tag ... ");
      String tagSample = getFile("tag.sample");
      Response response = postData("tag", tagSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST-SER"));
      String tag_id = response.then().extract().path("id");

      logger.info("--- mod-finance-test: Verifying only 1 tag was created ... ");
      getData("tag").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Creating fiscal year ... ");
      String fySample = getFile("fiscal_year.sample");
      response = postData("fiscal_year", fySample);
      response.then()
        .statusCode(201)
        .body("name", equalTo("Fiscal Year 2017"));
      String fy_id = response.then().extract().path("id");

      logger.info("--- mod-finance-test: Verifying only 1 fiscal year was created ... ");
      getData("fiscal_year").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching fiscal year with ID:"+ fy_id);
      getDataById("fiscal_year", fy_id).then()
        .statusCode(200)
        .body("id", equalTo(fy_id));

      logger.info("--- mod-finance-test: Editing fiscal year with ID:"+ fy_id);
      JSONObject fyJSON = new JSONObject(fySample);
      fyJSON.put("id", fy_id);
      fyJSON.put("name", "Fiscal Year 2017 B");
      response = putData("fiscal_year", fy_id, fyJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching fiscal year with ID:"+ fy_id);
      getDataById("fiscal_year", fy_id).then()
        .statusCode(200)
        .body("name", equalTo("Fiscal Year 2017 B"));


      logger.info("--- mod-finance-test: Creating ledger ... ");
      String ledgerSample = getFile("ledger.sample");
      response = postData("ledger", ledgerSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("MAIN-LIB"));
      String ledger_id = response.then().extract().path("id");

      logger.info("--- mod-finance-test: Verifying only 1 ledger was created ... ");
      getData("ledger").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching ledger with ID:"+ ledger_id);
      getDataById("ledger", ledger_id).then()
        .statusCode(200)
        .body("id", equalTo(ledger_id));

      logger.info("--- mod-finance-test: Editing ledger with ID:"+ ledger_id);
      JSONObject ledgerJSON = new JSONObject(ledgerSample);
      ledgerJSON.put("id", ledger_id);
      ledgerJSON.put("code", "MAIN-LIB-B");
      response = putData("ledger", ledger_id, ledgerJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching ledger with ID:"+ ledger_id);
      getDataById("ledger", ledger_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-B"));


      logger.info("--- mod-finance-test: Creating fund ... ");
      String fundSample = getFile("fund.sample");
      response = postData("fund", fundSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST"));
      String fund_id = response.then().extract().path("id");

      logger.info("--- mod-finance-test: Verifying only 1 fund was created ... ");
      getData("fund").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching fund with ID:"+ fund_id);
      getDataById("fund", fund_id).then()
        .statusCode(200)
        .body("id", equalTo(fund_id));

      logger.info("--- mod-finance-test: Editing fund with ID:"+ fund_id);
      JSONObject fundJSON = new JSONObject(fundSample);
      fundJSON.put("id", fund_id);
      fundJSON.put("code", "MAIN-LIB-FUND");
      fundJSON.put("ledger_id", ledger_id);
      response = putData("fund", fund_id, fundJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching fund with ID:"+ fund_id);
      getDataById("fund", fund_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-FUND"));


      logger.info("--- mod-finance-test: Creating budget ... ");
      String budgetSample = getFile("budget.sample");
      JSONObject budgetJSON = new JSONObject(budgetSample);
      budgetJSON.put("fund_id", fund_id);
      budgetJSON.put("fiscal_year_id", fy_id);
      response = postData("budget", budgetJSON.toString());
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST-2017"));
      String budget_id = response.then().extract().path("id");
      budgetJSON.put("id", budget_id);

      logger.info("--- mod-finance-test: Verifying only 1 budget was created ... ");
      getData("budget").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching budget with ID:"+ budget_id);
      getDataById("budget", budget_id).then()
        .statusCode(200)
        .body("id", equalTo(budget_id));

      logger.info("--- mod-finance-test: Editing fund with ID:"+ budget_id);
      budgetJSON.put("code", "MAIN-LIB-HIST-2017");
      response = putData("budget", budget_id, budgetJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching budget with ID:"+ budget_id);
      getDataById("budget", budget_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-HIST-2017"));


      logger.info("--- mod-finance-test: Creating transaction ... ");
      String transactionSample = getFile("transaction.sample");
      JSONObject transactionJSON = new JSONObject(transactionSample);
      transactionJSON.put("budget_id", budget_id);
      response = postData("transaction", transactionJSON.toString());
      response.then()
        .statusCode(201)
        .body("note", equalTo("PO_Line: History of Incas"));
      String transaction_id = response.then().extract().path("id");
      transactionJSON.put("id", transaction_id);

      logger.info("--- mod-finance-test: Verifying only 1 transaction was created ... ");
      getData("transaction").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching transaction with ID:"+ transaction_id);
      getDataById("transaction", transaction_id).then()
        .statusCode(200)
        .body("id", equalTo(transaction_id));

      logger.info("--- mod-finance-test: Editing transaction with ID:"+ transaction_id);
      transactionJSON.put("note", "PO_Line: The History of Incas");
      response = putData("transaction", transaction_id, transactionJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching transaction with ID:"+ transaction_id);
      getDataById("transaction", transaction_id).then()
        .statusCode(200)
        .body("note", equalTo("PO_Line: The History of Incas"));


      logger.info("--- mod-finance-test: Creating fund-distribution ... ");
      String distributionSample = getFile("fund_distribution.sample");
      JSONObject distributionJSON = new JSONObject(distributionSample);
      distributionJSON.put("budget_id", budget_id);
      response = postData("fund_distribution", distributionJSON.toString());
      response.then()
        .statusCode(201)
        .body("currency", equalTo("USD"));
      String distribution_id = response.then().extract().path("id");
      distributionJSON.put("id", distribution_id);

      logger.info("--- mod-finance-test: Verifying only 1 fund-distribution was created ... ");
      getData("fund_distribution").then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching fund-distribution with ID:"+ distribution_id);
      getDataById("fund_distribution", distribution_id).then()
        .statusCode(200)
        .body("id", equalTo(distribution_id));

      logger.info("--- mod-finance-test: Editing fund-distribution with ID:"+ distribution_id);
      distributionJSON.put("currency", "CAD");
      response = putData("fund_distribution", distribution_id, distributionJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching fund-distribution with ID:"+ distribution_id);
      getDataById("fund_distribution", distribution_id).then()
        .statusCode(200)
        .body("currency", equalTo("CAD"));


      logger.info("--- mod-finance-test: Deleting fund-distribution ... ");
      deleteData("fund_distribution", distribution_id).then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Deleting transaction ... ");
      deleteData("transaction", transaction_id).then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Deleting budget ... ");
      deleteData("budget", budget_id).then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Deleting fund ... ");
      deleteData("fund", fund_id).then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Deleting ledger ... ");
      deleteData("ledger", ledger_id).then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Deleting fiscal_year ... ");
      deleteData("fiscal_year", fy_id).then()
        .statusCode(204);

    }
    catch (Exception e) {
      context.fail("--- mod-finance-test: ERROR: " + e.getMessage());
    }
    async.complete();
  }

  private void prepareTenant() {
    String tenants = "{\"module_to\":\"" + moduleId + "\"}";
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .body(tenants)
      .post("/_/tenant")
      .then().log().ifValidationFails()
      .statusCode(201);
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
      .header("X-Okapi-Tenant",TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint);
  }

  private Response getDataById(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant",TENANT_NAME)
      .contentType(ContentType.JSON)
      .get(endpoint + "/{id}");
  }

  private Response postData(String endpoint, String input) {
    return given()
      .header("X-Okapi-Tenant", TENANT_NAME)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(input)
      .post(endpoint);
  }

  private Response putData(String endpoint, String id, String input) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .body(input)
      .put(endpoint + "/{id}");
  }

  private Response deleteData(String endpoint, String id) {
    return given()
      .pathParam("id", id)
      .header("X-Okapi-Tenant", TENANT_NAME)
      .contentType(ContentType.JSON)
      .delete(endpoint + "/{id}");
  }
}
