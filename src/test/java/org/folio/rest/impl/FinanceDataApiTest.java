package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundTags;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.resource.FinanceStorageFinanceData;
import org.folio.rest.persist.HelperUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


public class FinanceDataApiTest extends TestBase {
  private static final String TENANT_NAME = "fyfinancedata";
  private static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  private static final Logger logger = LogManager.getLogger();
  private static final String FINANCE_DATA_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageFinanceData.class);
  private static TenantJob tenantJob;
  private static final String FISCAL_YEAR_ID = UUID.randomUUID().toString();

  @BeforeAll
  public static void before() {
    logger.info("Create a new tenant loading the sample data");
    tenantJob = prepareTenant(TENANT_HEADER, true, true);
  }

  @AfterAll
  public static void after() {
    logger.info("Delete the created \"fyfinancedata\" tenant");
    purge(TENANT_HEADER);
    deleteTenant(tenantJob, TENANT_HEADER);
  }

  @Test
  public void positive_testGetQuery() {
    verifyCollectionQuantity(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==7a4c4d30-3b63-4102-8e2d-3ee5792d7d02)", 24, TENANT_HEADER);
    verifyCollectionQuantity(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==9b1d00d1-1f3d-4f1c-8e4b-0f1e3b7b1b1b)", 0, TENANT_HEADER);

    var response = getData(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==7a4c4d30-3b63-4102-8e2d-3ee5792d7d02)", TENANT_HEADER);
    var body = response.getBody().as(FyFinanceDataCollection.class);
    var actualFyFinanceData = body.getFyFinanceData().get(0);

    assertEquals("7a4c4d30-3b63-4102-8e2d-3ee5792d7d02", actualFyFinanceData.getFiscalYearId());
    assertNotNull(actualFyFinanceData.getFundId());
    assertNotNull(actualFyFinanceData.getFundCode());
    assertNotNull(actualFyFinanceData.getFundName());
    assertNotNull(actualFyFinanceData.getFundDescription());
    assertNotNull(actualFyFinanceData.getFundStatus());
    assertNotNull(actualFyFinanceData.getFundAcqUnitIds());
    assertNotNull(actualFyFinanceData.getBudgetId());
    assertNotNull(actualFyFinanceData.getBudgetName());
    assertNotNull(actualFyFinanceData.getBudgetStatus());
    assertNotNull(actualFyFinanceData.getBudgetInitialAllocation());
    assertNotNull(actualFyFinanceData.getBudgetCurrentAllocation());
    assertNotNull(actualFyFinanceData.getBudgetAllowableExpenditure());
    assertNotNull(actualFyFinanceData.getBudgetAllowableEncumbrance());
    assertNotNull(actualFyFinanceData.getBudgetAcqUnitIds());
  }

  @Test
  public void positive_testResponseOfGetWithParamsFiscalYearAndAcqUnitIds() {
    var acqUnitId = UUID.randomUUID().toString();
    var fiscalYearAcqUnitEndpoint = String.format("%s?query=(fiscalYearId==%s and fundAcqUnitIds=%s and budgetAcqUnitIds=%s)",
      FINANCE_DATA_ENDPOINT, FISCAL_YEAR_ID, acqUnitId, acqUnitId);
    createMockData(FISCAL_YEAR_ID, acqUnitId, "FY2088", "first");

    var response = getData(fiscalYearAcqUnitEndpoint, TENANT_HEADER);
    var body = response.getBody().as(FyFinanceDataCollection.class);
    var actualFyFinanceData = body.getFyFinanceData().get(0);

    assertEquals(FISCAL_YEAR_ID, actualFyFinanceData.getFiscalYearId());
    assertEquals(acqUnitId, actualFyFinanceData.getFundAcqUnitIds().get(0));
    assertEquals(acqUnitId, actualFyFinanceData.getBudgetAcqUnitIds().get(0));
  }

  @Test
  public void positive_testUpdateFinanceData() {
    var fiscalYearId = UUID.randomUUID().toString();
    var acqUnitId = UUID.randomUUID().toString();
    var expectedDescription = "UPDATED Description";
    var expectedTags = List.of("New tag");
    var expectedBudgetStatus = FyFinanceData.BudgetStatus.INACTIVE;
    var expectedNumber = 200.0;
    createMockData(fiscalYearId, acqUnitId, "FY2099", "second");

    var response = getData(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==" + fiscalYearId + ")", TENANT_HEADER);
    var body = response.getBody().as(FyFinanceDataCollection.class);
    var fyFinanceData = body.getFyFinanceData().get(0);

    // Set required fields difference values than before
    fyFinanceData.setFundDescription(expectedDescription);
    fyFinanceData.setFundTags(new FundTags().withTagList(expectedTags));
    fyFinanceData.setBudgetStatus(expectedBudgetStatus);
    fyFinanceData.setBudgetAllowableExpenditure(expectedNumber);
    fyFinanceData.setBudgetAllowableEncumbrance(expectedNumber);

    var updatedCollection = new FyFinanceDataCollection().withFyFinanceData(List.of(fyFinanceData)).withUpdateType(FyFinanceDataCollection.UpdateType.COMMIT).withTotalRecords(1);

    // Update finance data as a bulk
    var updateResponse = putData(FINANCE_DATA_ENDPOINT, JsonObject.mapFrom(updatedCollection).encodePrettily(), TENANT_HEADER);
    assertEquals(204, updateResponse.getStatusCode());

    // Get updated result
    var updatedResponse = getData(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==" + fiscalYearId + ")", TENANT_HEADER);
    var updatedBody = updatedResponse.getBody().as(FyFinanceDataCollection.class);
    var updatedFyFinanceData = updatedBody.getFyFinanceData().get(0);

    assertEquals(expectedDescription, updatedFyFinanceData.getFundDescription());
    assertEquals(expectedTags, updatedFyFinanceData.getFundTags().getTagList());
    assertEquals(expectedNumber, updatedFyFinanceData.getBudgetAllowableEncumbrance());
    assertEquals(expectedNumber, updatedFyFinanceData.getBudgetAllowableExpenditure());
  }

  private void createMockData(String fiscalYearId, String acqUnitId, String code, String name) {
    var fundId = UUID.randomUUID().toString();
    var ledgerId = UUID.randomUUID().toString();
    var budgetId = UUID.randomUUID().toString();

    var fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class)
      .withId(fiscalYearId).withCode(code);
    createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear, TENANT_HEADER);

    var ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class).withId(ledgerId)
      .withCode(code).withName(name).withFiscalYearOneId(fiscalYearId);
    createEntity(LEDGER.getEndpoint(), ledger, TENANT_HEADER);

    var fund = new JsonObject(getFile(FUND.getPathToSampleFile())).mapTo(Fund.class)
      .withId(fundId).withCode(code).withName(name).withLedgerId(ledgerId)
      .withFundTypeId(null).withAcqUnitIds(List.of(acqUnitId));
    createEntity(FUND.getEndpoint(), fund, TENANT_HEADER);

    var budget = new JsonObject(getFile(BUDGET.getPathToSampleFile())).mapTo(Budget.class)
      .withId(budgetId).withName(name)
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withInitialAllocation(100.0)
      .withAllowableExpenditure(101.0)
      .withAllowableEncumbrance(102.0)
      .withAcqUnitIds(List.of(acqUnitId));
    createEntity(BUDGET.getEndpoint(), budget, TENANT_HEADER);
  }

}
