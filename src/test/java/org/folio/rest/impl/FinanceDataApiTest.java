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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.resource.FinanceStorageFinanceData;
import org.folio.rest.persist.HelperUtils;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


public class FinanceDataApiTest extends TestBase {
  private static final String TENANT_NAME = "fyfinancedata";
  private static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  private static final Logger logger = LogManager.getLogger();
  private static final String FINANCE_DATA_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageFinanceData.class);
  private static TenantJob tenantJob;
  private static final String FINANCE_DATA_ACQ_ENDPOINT_FORMAT = "%s?query=(fiscalYearId==%s and fundAcqUnitIds=%s and budgetAcqUnitIds=%s)";

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
    var emptyResponse = getData(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==9b1d00d1-1f3d-4f1c-8e4b-0f1e3b7b1b1b)", TENANT_HEADER);
    var emptyBody = emptyResponse.getBody().as(FyFinanceDataCollection.class);

    assertTrue(emptyBody.getFyFinanceData().isEmpty());

    var fiscalYearId = UUID.randomUUID().toString();
    var acqUnitId = UUID.randomUUID().toString();
    var financeDataEndpoint = String.format(FINANCE_DATA_ACQ_ENDPOINT_FORMAT, FINANCE_DATA_ENDPOINT, fiscalYearId, acqUnitId, acqUnitId);

    createMockData(fiscalYearId, acqUnitId, "FY2077", "random");

    var response = getData(financeDataEndpoint, TENANT_HEADER);
    var body = response.getBody().as(FyFinanceDataCollection.class);
    var fyFinanceData = body.getFyFinanceData().getFirst();

    assertFalse(body.getFyFinanceData().isEmpty());
    assertEquals(fiscalYearId, fyFinanceData.getFiscalYearId());
    assertNotNull(fyFinanceData.getFundId());
    assertNotNull(fyFinanceData.getFundCode());
    assertNotNull(fyFinanceData.getFundName());
    assertNotNull(fyFinanceData.getFundDescription());
    assertNotNull(fyFinanceData.getFundStatus());
    assertNotNull(fyFinanceData.getFundAcqUnitIds());
    assertNotNull(fyFinanceData.getBudgetId());
    assertNotNull(fyFinanceData.getBudgetName());
    assertNotNull(fyFinanceData.getBudgetStatus());
    assertNotNull(fyFinanceData.getBudgetInitialAllocation());
    assertNotNull(fyFinanceData.getBudgetCurrentAllocation());
    assertNotNull(fyFinanceData.getBudgetAllowableExpenditure());
    assertNotNull(fyFinanceData.getBudgetAllowableEncumbrance());
    assertNotNull(fyFinanceData.getBudgetAcqUnitIds());
  }

  @Test
  public void positive_testResponseOfGetWithParamsFiscalYearAndAcqUnitIds() {
    var fiscalYearId = UUID.randomUUID().toString();
    var acqUnitId = UUID.randomUUID().toString();
    var financeDataEndpoint = String.format(FINANCE_DATA_ACQ_ENDPOINT_FORMAT, FINANCE_DATA_ENDPOINT, fiscalYearId, acqUnitId, acqUnitId);

    createMockData(fiscalYearId, acqUnitId, "FY2088", "first");

    var response = getData(financeDataEndpoint, TENANT_HEADER);
    var body = response.getBody().as(FyFinanceDataCollection.class);
    var actualFyFinanceData = body.getFyFinanceData().getFirst();

    assertEquals(fiscalYearId, actualFyFinanceData.getFiscalYearId());
    assertEquals(acqUnitId, actualFyFinanceData.getFundAcqUnitIds().getFirst());
    assertEquals(acqUnitId, actualFyFinanceData.getBudgetAcqUnitIds().getFirst());
  }

  @Test
  public void positive_testUpdateFinanceData() {
    var fiscalYearId = UUID.randomUUID().toString();
    var acqUnitId = UUID.randomUUID().toString();
    var expectedDescription = "UPDATED Description";
    var expectedTags = List.of("New tag");
    var expectedBudgetStatus = "Inactive";
    var expectedNumber = 200.0;
    var financeDataEndpoint = String.format(FINANCE_DATA_ACQ_ENDPOINT_FORMAT, FINANCE_DATA_ENDPOINT, fiscalYearId, acqUnitId, acqUnitId);

    createMockData(fiscalYearId, acqUnitId, "FY2099", "second");

    var response = getData(financeDataEndpoint, TENANT_HEADER);
    var body = response.getBody().as(FyFinanceDataCollection.class);
    var fyFinanceData = body.getFyFinanceData().getFirst();

    // Set required fields difference values than before
    fyFinanceData.setFundDescription(expectedDescription);
    fyFinanceData.setFundTags(new FundTags().withTagList(expectedTags));
    fyFinanceData.setBudgetStatus(expectedBudgetStatus);
    fyFinanceData.setBudgetAllowableExpenditure(expectedNumber);
    fyFinanceData.setBudgetAllowableEncumbrance(expectedNumber);

    var updatedCollection = new FyFinanceDataCollection()
      .withFyFinanceData(List.of(fyFinanceData))
      .withUpdateType(FyFinanceDataCollection.UpdateType.COMMIT).withTotalRecords(1);

    // Update finance data as a bulk
    var updateResponse = putData(FINANCE_DATA_ENDPOINT, JsonObject.mapFrom(updatedCollection).encodePrettily(), TENANT_HEADER);
    assertEquals(200, updateResponse.getStatusCode());

    // Get updated result
    var updatedResponse = getData(financeDataEndpoint, TENANT_HEADER);
    var updatedBody = updatedResponse.getBody().as(FyFinanceDataCollection.class);
    var updatedFyFinanceData = updatedBody.getFyFinanceData().getFirst();

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
      .withId(fiscalYearId).withCode(code).withName("TEST");
    createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear, TENANT_HEADER);

    var ledger = new Ledger()
      .withId(ledgerId)
      .withCode(code).withName(name)
      .withFiscalYearOneId(fiscalYearId)
      .withLedgerStatus(Ledger.LedgerStatus.ACTIVE);
    createEntity(LEDGER.getEndpoint(), ledger, TENANT_HEADER);

    var fund = new Fund()
      .withId(fundId).withCode(code).withName(name).withDescription("Description")
      .withLedgerId(ledgerId)
      .withFundStatus(Fund.FundStatus.ACTIVE)
      .withFundTypeId(null).withAcqUnitIds(List.of(acqUnitId));
    createEntity(FUND.getEndpoint(), fund, TENANT_HEADER);

    var budget = new Budget()
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
