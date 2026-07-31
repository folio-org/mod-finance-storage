package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.BUDGET_EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.EXPENSE_CLASS;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.GROUP;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyCollection;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYearHierarchy;
import org.folio.rest.persist.HelperUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FiscalYearHierarchyApiTest extends TestBase {
  private static final String TENANT_NAME = "fiscalyearhierarchy";
  private static final Header TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  private static final Logger logger = LogManager.getLogger();
  private static final String FISCAL_YEAR_HIERARCHY_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageFiscalYearHierarchy.class);
  private static TenantJob tenantJob;

  @BeforeAll
  public static void before() {
    logger.info("Create a new tenant loading the sample data");
    tenantJob = prepareTenant(TENANT_HEADER, true, true);
  }

  @AfterAll
  public static void after() {
    logger.info("Delete the created \"fiscalyearhierarchy\" tenant");
    purge(TENANT_HEADER);
    deleteTenant(tenantJob, TENANT_HEADER);
  }

  @Test
  public void positive_testGetHierarchyForFiscalYear() {
    var fiscalYearId = UUID.randomUUID().toString();
    var ledgerId = UUID.randomUUID().toString();
    var groupOneId = UUID.randomUUID().toString();
    var groupTwoId = UUID.randomUUID().toString();
    var groupedFundId = UUID.randomUUID().toString();
    var ungroupedFundId = UUID.randomUUID().toString();
    var groupedBudgetId = UUID.randomUUID().toString();
    var ungroupedBudgetId = UUID.randomUUID().toString();
    var expenseClassId = UUID.randomUUID().toString();

    var fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class)
      .withId(fiscalYearId).withCode("FY2077hier").withName("TEST");
    createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear, TENANT_HEADER);

    var ledger = new Ledger()
      .withId(ledgerId).withCode("HIER").withName("Hierarchy ledger")
      .withFiscalYearOneId(fiscalYearId)
      .withLedgerStatus(Ledger.LedgerStatus.ACTIVE)
      .withRestrictEncumbrance(true).withRestrictExpenditures(true);
    createEntity(LEDGER.getEndpoint(), ledger, TENANT_HEADER);

    var groupOne = new Group().withId(groupOneId).withCode("GRP1").withName("Group one").withStatus(Group.Status.ACTIVE);
    createEntity(GROUP.getEndpoint(), groupOne, TENANT_HEADER);
    var groupTwo = new Group().withId(groupTwoId).withCode("GRP2").withName("Group two").withStatus(Group.Status.ACTIVE);
    createEntity(GROUP.getEndpoint(), groupTwo, TENANT_HEADER);

    var groupedFund = new Fund()
      .withId(groupedFundId).withCode("GFND").withName("Grouped fund")
      .withLedgerId(ledgerId).withFundStatus(Fund.FundStatus.ACTIVE);
    createEntity(FUND.getEndpoint(), groupedFund, TENANT_HEADER);

    var ungroupedFund = new Fund()
      .withId(ungroupedFundId).withCode("UFND").withName("Ungrouped fund")
      .withLedgerId(ledgerId).withFundStatus(Fund.FundStatus.ACTIVE);
    createEntity(FUND.getEndpoint(), ungroupedFund, TENANT_HEADER);

    var groupedBudget = new Budget()
      .withId(groupedBudgetId).withName("Grouped budget")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFiscalYearId(fiscalYearId).withFundId(groupedFundId)
      .withInitialAllocation(1000.0);
    createEntity(BUDGET.getEndpoint(), groupedBudget, TENANT_HEADER);

    var ungroupedBudget = new Budget()
      .withId(ungroupedBudgetId).withName("Ungrouped budget")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFiscalYearId(fiscalYearId).withFundId(ungroupedFundId)
      .withInitialAllocation(500.0);
    createEntity(BUDGET.getEndpoint(), ungroupedBudget, TENANT_HEADER);

    var expenseClass = new ExpenseClass().withId(expenseClassId).withCode("PRINT").withName("Print");
    createEntity(EXPENSE_CLASS.getEndpoint(), expenseClass, TENANT_HEADER);

    var budgetExpenseClass = new BudgetExpenseClass()
      .withBudgetId(groupedBudgetId).withExpenseClassId(expenseClassId)
      .withStatus(BudgetExpenseClass.Status.ACTIVE);
    createEntity(BUDGET_EXPENSE_CLASS.getEndpoint(), budgetExpenseClass, TENANT_HEADER);

    createEntity(GROUP_FUND_FY.getEndpoint(),
      new GroupFundFiscalYear().withGroupId(groupOneId).withFundId(groupedFundId).withFiscalYearId(fiscalYearId)
        .withBudgetId(groupedBudgetId),
      TENANT_HEADER);
    createEntity(GROUP_FUND_FY.getEndpoint(),
      new GroupFundFiscalYear().withGroupId(groupTwoId).withFundId(groupedFundId).withFiscalYearId(fiscalYearId)
        .withBudgetId(groupedBudgetId),
      TENANT_HEADER);

    var response = getData(FISCAL_YEAR_HIERARCHY_ENDPOINT + "?fiscalYearId=" + fiscalYearId, TENANT_HEADER);
    var body = response.getBody().as(FiscalYearHierarchyCollection.class);

    assertEquals(1, body.getTotalRecords());
    var hierarchy = body.getFiscalYearHierarchies().getFirst();
    assertEquals(ledgerId, hierarchy.getLedgerId());
    assertEquals(fiscalYearId, hierarchy.getFiscalYearId());

    assertEquals(2, hierarchy.getGroups().size());
    for (var group : hierarchy.getGroups()) {
      assertEquals(1, group.getFunds().size());
      var fund = group.getFunds().getFirst();
      assertEquals(groupedFundId, fund.getFundId());
      assertEquals(groupedBudgetId, fund.getFiscalYearHierarchyBudget().getBudgetId());
      assertEquals(1, fund.getFiscalYearHierarchyBudget().getBudgetExpenseClasses().size());
      assertEquals(expenseClassId, fund.getFiscalYearHierarchyBudget().getBudgetExpenseClasses().getFirst().getExpenseClassId());
    }

    assertEquals(1, hierarchy.getFunds().size());
    var ungrouped = hierarchy.getFunds().getFirst();
    assertEquals(ungroupedFundId, ungrouped.getFundId());
    assertEquals(ungroupedBudgetId, ungrouped.getFiscalYearHierarchyBudget().getBudgetId());
    assertTrue(ungrouped.getFiscalYearHierarchyBudget().getBudgetExpenseClasses().isEmpty());
  }

  @Test
  public void negative_testMissingFiscalYearId() {
    getData(FISCAL_YEAR_HIERARCHY_ENDPOINT, TENANT_HEADER)
      .then()
      .statusCode(400);
  }

}
