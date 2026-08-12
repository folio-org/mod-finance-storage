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
import org.folio.rest.jaxrs.model.FiscalYearHierarchy;
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

    var expenseClass = new ExpenseClass().withId(expenseClassId).withCode("HIERPRINT").withName("Hierarchy print");
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
  public void positive_testFilterHierarchyByStatus() {
    var fiscalYearId = UUID.randomUUID().toString();
    var ledgerId = UUID.randomUUID().toString();
    var groupActiveId = UUID.randomUUID().toString();
    var groupInactiveId = UUID.randomUUID().toString();
    var fundActiveId = UUID.randomUUID().toString();
    var fundInactiveId = UUID.randomUUID().toString();
    var budgetActiveId = UUID.randomUUID().toString();
    var budgetFrozenId = UUID.randomUUID().toString();
    var expenseClassActiveId = UUID.randomUUID().toString();
    var expenseClassInactiveId = UUID.randomUUID().toString();

    var fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class)
      .withId(fiscalYearId).withCode("FY2077filt").withName("TEST");
    createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear, TENANT_HEADER);

    var ledger = new Ledger()
      .withId(ledgerId).withCode("HIERF").withName("Hierarchy filter ledger")
      .withFiscalYearOneId(fiscalYearId)
      .withLedgerStatus(Ledger.LedgerStatus.ACTIVE)
      .withRestrictEncumbrance(true).withRestrictExpenditures(true);
    createEntity(LEDGER.getEndpoint(), ledger, TENANT_HEADER);

    var groupActive = new Group().withId(groupActiveId).withCode("FGA").withName("Filter group active")
      .withStatus(Group.Status.ACTIVE);
    createEntity(GROUP.getEndpoint(), groupActive, TENANT_HEADER);
    var groupInactive = new Group().withId(groupInactiveId).withCode("FGI").withName("Filter group inactive")
      .withStatus(Group.Status.INACTIVE);
    createEntity(GROUP.getEndpoint(), groupInactive, TENANT_HEADER);

    var fundActive = new Fund()
      .withId(fundActiveId).withCode("FFA").withName("Filter fund active")
      .withLedgerId(ledgerId).withFundStatus(Fund.FundStatus.ACTIVE);
    createEntity(FUND.getEndpoint(), fundActive, TENANT_HEADER);
    var fundInactive = new Fund()
      .withId(fundInactiveId).withCode("FFI").withName("Filter fund inactive")
      .withLedgerId(ledgerId).withFundStatus(Fund.FundStatus.INACTIVE);
    createEntity(FUND.getEndpoint(), fundInactive, TENANT_HEADER);

    var budgetActive = new Budget()
      .withId(budgetActiveId).withName("Filter budget active")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFiscalYearId(fiscalYearId).withFundId(fundActiveId)
      .withInitialAllocation(1000.0);
    createEntity(BUDGET.getEndpoint(), budgetActive, TENANT_HEADER);
    var budgetFrozen = new Budget()
      .withId(budgetFrozenId).withName("Filter budget frozen")
      .withBudgetStatus(Budget.BudgetStatus.FROZEN)
      .withFiscalYearId(fiscalYearId).withFundId(fundInactiveId)
      .withInitialAllocation(500.0);
    createEntity(BUDGET.getEndpoint(), budgetFrozen, TENANT_HEADER);

    var expenseClassActive = new ExpenseClass().withId(expenseClassActiveId).withCode("FECA").withName("Filter EC active");
    createEntity(EXPENSE_CLASS.getEndpoint(), expenseClassActive, TENANT_HEADER);
    var expenseClassInactive = new ExpenseClass().withId(expenseClassInactiveId).withCode("FECI").withName("Filter EC inactive");
    createEntity(EXPENSE_CLASS.getEndpoint(), expenseClassInactive, TENANT_HEADER);

    createEntity(BUDGET_EXPENSE_CLASS.getEndpoint(),
      new BudgetExpenseClass().withBudgetId(budgetActiveId).withExpenseClassId(expenseClassActiveId)
        .withStatus(BudgetExpenseClass.Status.ACTIVE),
      TENANT_HEADER);
    createEntity(BUDGET_EXPENSE_CLASS.getEndpoint(),
      new BudgetExpenseClass().withBudgetId(budgetActiveId).withExpenseClassId(expenseClassInactiveId)
        .withStatus(BudgetExpenseClass.Status.INACTIVE),
      TENANT_HEADER);

    createEntity(GROUP_FUND_FY.getEndpoint(),
      new GroupFundFiscalYear().withGroupId(groupActiveId).withFundId(fundActiveId).withFiscalYearId(fiscalYearId)
        .withBudgetId(budgetActiveId),
      TENANT_HEADER);
    createEntity(GROUP_FUND_FY.getEndpoint(),
      new GroupFundFiscalYear().withGroupId(groupInactiveId).withFundId(fundActiveId).withFiscalYearId(fiscalYearId)
        .withBudgetId(budgetActiveId),
      TENANT_HEADER);

    var baseQuery = FISCAL_YEAR_HIERARCHY_ENDPOINT + "?fiscalYearId=" + fiscalYearId;

    // no filters: both groups (fund active in both), plus the ungrouped inactive fund
    var unfiltered = getData(baseQuery, TENANT_HEADER).getBody().as(FiscalYearHierarchyCollection.class)
      .getFiscalYearHierarchies().getFirst();
    assertEquals(2, unfiltered.getGroups().size());
    assertEquals(1, unfiltered.getFunds().size());

    // ledgerStatus: matches the single ledger, or excludes it entirely
    assertEquals(1, hierarchyCount(baseQuery + "&ledgerStatus=Active"));
    assertEquals(0, hierarchyCount(baseQuery + "&ledgerStatus=Frozen"));

    // groupStatus: only the matching group remains, and ungrouped funds are excluded
    var groupActiveOnly = firstHierarchy(baseQuery + "&groupStatus=Active");
    assertEquals(1, groupActiveOnly.getGroups().size());
    assertEquals(groupActiveId, groupActiveOnly.getGroups().getFirst().getGroupId());
    assertTrue(groupActiveOnly.getFunds().isEmpty());

    var groupInactiveOnly = firstHierarchy(baseQuery + "&groupStatus=Inactive");
    assertEquals(1, groupInactiveOnly.getGroups().size());
    assertEquals(groupInactiveId, groupInactiveOnly.getGroups().getFirst().getGroupId());

    // fundStatus: excludes the other fund's rows, grouped or ungrouped
    var fundActiveOnly = firstHierarchy(baseQuery + "&fundStatus=Active");
    assertEquals(2, fundActiveOnly.getGroups().size());
    assertTrue(fundActiveOnly.getFunds().isEmpty());

    var fundInactiveOnly = firstHierarchy(baseQuery + "&fundStatus=Inactive");
    assertTrue(fundInactiveOnly.getGroups().isEmpty());
    assertEquals(1, fundInactiveOnly.getFunds().size());
    assertEquals(fundInactiveId, fundInactiveOnly.getFunds().getFirst().getFundId());

    // budgetStatus: same shape as fundStatus here, since each fund has exactly one budget
    var budgetActiveOnly = firstHierarchy(baseQuery + "&budgetStatus=Active");
    assertEquals(2, budgetActiveOnly.getGroups().size());
    assertTrue(budgetActiveOnly.getFunds().isEmpty());

    var budgetFrozenOnly = firstHierarchy(baseQuery + "&budgetStatus=Frozen");
    assertTrue(budgetFrozenOnly.getGroups().isEmpty());
    assertEquals(1, budgetFrozenOnly.getFunds().size());

    // expenseClassStatus: prunes the nested array without dropping the fund/budget/group
    var ecActiveOnly = firstHierarchy(baseQuery + "&expenseClassStatus=Active");
    var ecActiveClasses = ecActiveOnly.getGroups().getFirst().getFunds().getFirst()
      .getFiscalYearHierarchyBudget().getBudgetExpenseClasses();
    assertEquals(1, ecActiveClasses.size());
    assertEquals(expenseClassActiveId, ecActiveClasses.getFirst().getExpenseClassId());

    var ecInactiveOnly = firstHierarchy(baseQuery + "&expenseClassStatus=Inactive");
    var ecInactiveClasses = ecInactiveOnly.getGroups().getFirst().getFunds().getFirst()
      .getFiscalYearHierarchyBudget().getBudgetExpenseClasses();
    assertEquals(1, ecInactiveClasses.size());
    assertEquals(expenseClassInactiveId, ecInactiveClasses.getFirst().getExpenseClassId());

    // combining filters ANDs them together
    var combined = firstHierarchy(baseQuery + "&groupStatus=Active&fundStatus=Active");
    assertEquals(1, combined.getGroups().size());
    assertTrue(combined.getFunds().isEmpty());
  }

  private FiscalYearHierarchy firstHierarchy(String query) {
    return getData(query, TENANT_HEADER).getBody().as(FiscalYearHierarchyCollection.class)
      .getFiscalYearHierarchies().getFirst();
  }

  private int hierarchyCount(String query) {
    return getData(query, TENANT_HEADER).getBody().as(FiscalYearHierarchyCollection.class).getTotalRecords();
  }

  @Test
  public void negative_testMissingFiscalYearId() {
    getData(FISCAL_YEAR_HIERARCHY_ENDPOINT, TENANT_HEADER)
      .then()
      .statusCode(400);
  }

}
