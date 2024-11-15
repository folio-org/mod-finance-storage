package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.http.Header;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
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
    verifyCollectionQuantity(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==7a4c4d30-3b63-4102-8e2d-3ee5792d7d02)", 21, TENANT_HEADER);
    verifyCollectionQuantity(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==9b1d00d1-1f3d-4f1c-8e4b-0f1e3b7b1b1b)", 0, TENANT_HEADER);
  }

  @Test
  public void positive_testResponseOfGet() {
    var response = getData(FINANCE_DATA_ENDPOINT + "?query=(fiscalYearId==7a4c4d30-3b63-4102-8e2d-3ee5792d7d02)", TENANT_HEADER);
    var body = response.getBody().as(FyFinanceDataCollection.class);

    var fyFinanceDataExample = body.getFyFinanceData().get(0);
    assertEquals("7a4c4d30-3b63-4102-8e2d-3ee5792d7d02", fyFinanceDataExample.getFiscalYearId());
    assertNotNull(fyFinanceDataExample.getFundId());
    assertNotNull(fyFinanceDataExample.getFundCode());
    assertNotNull(fyFinanceDataExample.getFundName());
    assertNotNull(fyFinanceDataExample.getFundDescription());
    assertNotNull(fyFinanceDataExample.getFundStatus());
    assertNotNull(fyFinanceDataExample.getBudgetId());
    assertNotNull(fyFinanceDataExample.getBudgetName());
    assertNotNull(fyFinanceDataExample.getBudgetStatus());
    assertNotNull(fyFinanceDataExample.getBudgetInitialAllocation());
    assertNotNull(fyFinanceDataExample.getBudgetCurrentAllocation());
    assertNotNull(fyFinanceDataExample.getBudgetAllowableExpenditure());
    assertNotNull(fyFinanceDataExample.getBudgetAllowableEncumbrance());
  }
}
