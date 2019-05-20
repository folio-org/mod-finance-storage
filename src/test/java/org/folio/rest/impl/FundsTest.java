package org.folio.rest.impl;


import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

public class FundsTest extends TestBase {
  private static final String FUND_DISTRIBUTION_ENDPOINT = "/fund_distribution";
  private static final String TRANSACTION_ENDPOINT = "/transaction";
  private final Logger logger = LoggerFactory.getLogger("okapi");

  // Validates that there are zero vendor records in the DB
  private void verifyInitialDBState() throws MalformedURLException {

    // Validate 200 response and that there are zero records
    getData(BUDGET.getEndpoint()).then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("budgets", empty());

    getData(FISCAL_YEAR.getEndpoint()).then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("fiscal_years", empty());

    getData(FUND_DISTRIBUTION_ENDPOINT).then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("distributions", empty());

    getData(FUND.getEndpoint()).then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("funds", empty());

    getData(LEDGER.getEndpoint()).then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("ledgers", empty());

    getData(TRANSACTION_ENDPOINT).then()
      .statusCode(200)
      .body("total_records", equalTo(0))
      .body("transactions", empty());
  }

  @Test
  public void testOrders() throws MalformedURLException {
      logger.info("--- mod-finance-test: Verifying empty database ... ");
      verifyInitialDBState();

      logger.info("--- mod-finance-test: Creating fiscal year ... ");
      String fySample = getFile("fiscal_year.sample");
      Response response = postData(FISCAL_YEAR.getEndpoint(), fySample);
      response.then()
        .statusCode(201)
        .body("name", equalTo("Fiscal Year 2017"));
      String fy_id = response.then().extract().path("id");

      logger.info("--- mod-finance-test: Verifying only 1 fiscal year was created ... ");
      getData(FISCAL_YEAR.getEndpoint()).then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching fiscal year with ID:" + fy_id);
      getDataById(FISCAL_YEAR.getEndpoint(), fy_id).then()
        .statusCode(200)
        .body("id", equalTo(fy_id));

      logger.info("--- mod-finance-test: Editing fiscal year with ID:" + fy_id);
      JSONObject fyJSON = new JSONObject(fySample);
      fyJSON.put("id", fy_id);
      fyJSON.put("name", "Fiscal Year 2017 B");
      response = putData(FISCAL_YEAR.getEndpoint(), fy_id, fyJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching fiscal year with ID:" + fy_id);
      getDataById(FISCAL_YEAR.getEndpoint(), fy_id).then()
        .statusCode(200)
        .body("name", equalTo("Fiscal Year 2017 B"));


      logger.info("--- mod-finance-test: Creating ledger ... ");
      String ledgerSample = getFile("ledger.sample");
      response = postData(LEDGER.getEndpoint(), ledgerSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("MAIN-LIB"));
      String ledger_id = response.then().extract().path("id");

      logger.info("--- mod-finance-test: Verifying only 1 ledger was created ... ");
      getData(LEDGER.getEndpoint()).then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching ledger with ID:" + ledger_id);
      getDataById(LEDGER.getEndpoint(), ledger_id).then()
        .statusCode(200)
        .body("id", equalTo(ledger_id));

      logger.info("--- mod-finance-test: Editing ledger with ID:" + ledger_id);
      JSONObject ledgerJSON = new JSONObject(ledgerSample);
      ledgerJSON.put("id", ledger_id);
      ledgerJSON.put("code", "MAIN-LIB-B");
      response = putData(LEDGER.getEndpoint(), ledger_id, ledgerJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching ledger with ID:" + ledger_id);
      getDataById(LEDGER.getEndpoint(), ledger_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-B"));


      logger.info("--- mod-finance-test: Creating fund ... ");
      String fundSample = getFile("fund.sample");
      response = postData(FUND.getEndpoint(), fundSample);
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST"));
      String fund_id = response.then().extract().path("id");

      logger.info("--- mod-finance-test: Verifying only 1 fund was created ... ");
      getData(FUND.getEndpoint()).then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching fund with ID:" + fund_id);
      getDataById(FUND.getEndpoint(), fund_id).then()
        .statusCode(200)
        .body("id", equalTo(fund_id));

      logger.info("--- mod-finance-test: Editing fund with ID:" + fund_id);
      JSONObject fundJSON = new JSONObject(fundSample);
      fundJSON.put("id", fund_id);
      fundJSON.put("code", "MAIN-LIB-FUND");
      fundJSON.put("ledger_id", ledger_id);
      response = putData(FUND.getEndpoint(), fund_id, fundJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching fund with ID:" + fund_id);
      getDataById(FUND.getEndpoint(), fund_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-FUND"));


      logger.info("--- mod-finance-test: Creating budget ... ");
      String budgetSample = getFile("budget.sample");
      JSONObject budgetJSON = new JSONObject(budgetSample);
      budgetJSON.put("fund_id", fund_id);
      budgetJSON.put("fiscal_year_id", fy_id);
      response = postData(BUDGET.getEndpoint(), budgetJSON.toString());
      response.then()
        .statusCode(201)
        .body("code", equalTo("HIST-2017"));
      String budget_id = response.then().extract().path("id");
      budgetJSON.put("id", budget_id);

      logger.info("--- mod-finance-test: Verifying only 1 budget was created ... ");
      getData(BUDGET.getEndpoint()).then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching budget with ID:" + budget_id);
      getDataById(BUDGET.getEndpoint(), budget_id).then()
        .statusCode(200)
        .body("id", equalTo(budget_id));

      logger.info("--- mod-finance-test: Editing fund with ID:" + budget_id);
      budgetJSON.put("code", "MAIN-LIB-HIST-2017");
      response = putData(BUDGET.getEndpoint(), budget_id, budgetJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching budget with ID:" + budget_id);
      getDataById(BUDGET.getEndpoint(), budget_id).then()
        .statusCode(200)
        .body("code", equalTo("MAIN-LIB-HIST-2017"));


      logger.info("--- mod-finance-test: Creating transaction ... ");
      String transactionSample = getFile("transaction.sample");
      JSONObject transactionJSON = new JSONObject(transactionSample);
      transactionJSON.put("budget_id", budget_id);
      response = postData(TRANSACTION_ENDPOINT, transactionJSON.toString());
      response.then()
        .statusCode(201)
        .body("note", equalTo("PO_Line: History of Incas"));
      String transaction_id = response.then().extract().path("id");
      transactionJSON.put("id", transaction_id);

      logger.info("--- mod-finance-test: Verifying only 1 transaction was created ... ");
      getData(TRANSACTION_ENDPOINT).then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching transaction with ID:" + transaction_id);
      getDataById(TRANSACTION_ENDPOINT, transaction_id).then()
        .statusCode(200)
        .body("id", equalTo(transaction_id));

      logger.info("--- mod-finance-test: Editing transaction with ID:" + transaction_id);
      transactionJSON.put("note", "PO_Line: The History of Incas");
      response = putData(TRANSACTION_ENDPOINT, transaction_id, transactionJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching transaction with ID:" + transaction_id);
      getDataById(TRANSACTION_ENDPOINT, transaction_id).then()
        .statusCode(200)
        .body("note", equalTo("PO_Line: The History of Incas"));


      logger.info("--- mod-finance-test: Creating fund-distribution ... ");
      String distributionSample = getFile("fund_distribution.sample");
      JSONObject distributionJSON = new JSONObject(distributionSample);
      distributionJSON.put("budget_id", budget_id);
      response = postData(FUND_DISTRIBUTION_ENDPOINT, distributionJSON.toString());
      response.then()
        .statusCode(201)
        .body("currency", equalTo("USD"));
      String distribution_id = response.then().extract().path("id");
      distributionJSON.put("id", distribution_id);

      logger.info("--- mod-finance-test: Verifying only 1 fund-distribution was created ... ");
      getData(FUND_DISTRIBUTION_ENDPOINT).then()
        .statusCode(200)
        .body("total_records", equalTo(1));

      logger.info("--- mod-finance-test: Fetching fund-distribution with ID:" + distribution_id);
      getDataById(FUND_DISTRIBUTION_ENDPOINT, distribution_id).then()
        .statusCode(200)
        .body("id", equalTo(distribution_id));

      logger.info("--- mod-finance-test: Editing fund-distribution with ID:" + distribution_id);
      distributionJSON.put("currency", "CAD");
      response = putData(FUND_DISTRIBUTION_ENDPOINT, distribution_id, distributionJSON.toString());
      response.then()
        .statusCode(204);

      logger.info("--- mod-finance-test: Fetching fund-distribution with ID:" + distribution_id);
      getDataById(FUND_DISTRIBUTION_ENDPOINT, distribution_id).then()
        .statusCode(200)
        .body("currency", equalTo("CAD"));

      logger.info("--- mod-finance-test: Deleting fund-distribution ... ");
      deleteDataSuccess(FUND_DISTRIBUTION_ENDPOINT, distribution_id);

      logger.info("--- mod-finance-test: Deleting transaction ... ");
      deleteDataSuccess(TRANSACTION_ENDPOINT, transaction_id);

      logger.info("--- mod-finance-test: Deleting budget ... ");
      deleteDataSuccess(BUDGET.getEndpoint(), budget_id);

      logger.info("--- mod-finance-test: Deleting fund ... ");
      deleteDataSuccess(FUND.getEndpoint(), fund_id);

      logger.info("--- mod-finance-test: Deleting ledger ... ");
      deleteDataSuccess(LEDGER.getEndpoint(), ledger_id);

      logger.info("--- mod-finance-test: Deleting fiscal_year ... ");
      deleteDataSuccess(FISCAL_YEAR.getEndpoint(), fy_id);
  }
}
