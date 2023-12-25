package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.persist.HelperUtils.getEndpoint;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.GROUP;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupBudgets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

public class GroupBudgetTest extends TestBase {
  private static final Logger logger = LogManager.getLogger(GroupBudgetTest.class);

  private static final String TENANT_NAME = "acqunitsearch";
  private static final Header VIEW_SEARCH_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  private static final String EMPTY_ACQ_UNITS_QUERY = "?query=groups.acqUnitIds==[]&limit=20";
  private static final String QUERY_ACQ_UNIT_BY_ID = "?query=groups.acqUnitIds=%s&limit=20";
  private static TenantJob tenantJob;

  @BeforeAll
  public static void before() {
    logger.info("Create a new tenant loading the sample data");
    tenantJob = prepareTenant(VIEW_SEARCH_TENANT_HEADER, true, true);
  }

  @AfterAll
  public static void after() {
    logger.info("Delete the created \"acqunitsearch\" tenant");
    purge(VIEW_SEARCH_TENANT_HEADER);
    deleteTenant(tenantJob, VIEW_SEARCH_TENANT_HEADER);
  }

  @Test
  public void testSearchByGroupAcqUnitsIds() {
    Group group = new JsonObject(getFile(GROUP.getSampleFileName())).mapTo(Group.class);
    FiscalYear fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getSampleFileName())).mapTo(FiscalYear.class); // one of fiscal years loaded with sample data
    Budget budget = new JsonObject(getFile(BUDGET.getSampleFileName())).mapTo(Budget.class); // one of budget loaded with sample data

    group.setId(null);
    group.setCode("TEST");
    group.setName("Test");

    String acqUnitId = UUID.randomUUID().toString();
    group.getAcqUnitIds().add(acqUnitId);

    //create new group with acqUnitIds in addition to one loaded with sample data
    Group responseGroup = postData(GROUP.getEndpoint(), JsonObject.mapFrom(group).encodePrettily(), VIEW_SEARCH_TENANT_HEADER).as(Group.class);

    // Get with empty query returns all budgets we have in table
    BudgetCollection completeBudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class), VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(completeBudgetCollection.getTotalRecords(), equalTo(BUDGET.getInitialQuantity()));

    //search for budgets assigned to group from sample data(without acqUnitIds), size must be equal to number of GROUP_FUND_FY records
    BudgetCollection group1BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + EMPTY_ACQ_UNITS_QUERY, VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(group1BudgetCollection.getBudgets(), hasSize(GROUP_FUND_FY.getInitialQuantity()));

    //search for budgets by groups.acqUnitIds={id generated for new group}, expected empty result since no fund assigned to new group yet
    BudgetCollection group2BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + String.format(QUERY_ACQ_UNIT_BY_ID, acqUnitId), VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(group2BudgetCollection.getBudgets(), hasSize(0));

    //add fund and related budget to new group, these fund and budget already added to group from sample data
    GroupFundFiscalYear groupFundFiscalYear = new GroupFundFiscalYear()
      .withFiscalYearId(fiscalYear.getId())
      .withFundId(budget.getFundId())
      .withBudgetId(budget.getId())
      .withGroupId(responseGroup.getId());
    postData(GROUP_FUND_FY.getEndpoint(), JsonObject.mapFrom(groupFundFiscalYear).encodePrettily(), VIEW_SEARCH_TENANT_HEADER).as(GroupFundFiscalYear.class);

    //result for query by empty groups.acqUnitIds hasn't changed
    group1BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + EMPTY_ACQ_UNITS_QUERY, VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(group1BudgetCollection.getBudgets(), hasSize(GROUP_FUND_FY.getInitialQuantity()));

    // result for query by groups.acqUnitIds={id generated for new group} has changed
    group2BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + String.format(QUERY_ACQ_UNIT_BY_ID, acqUnitId), VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(group2BudgetCollection.getBudgets(), hasSize(1));

    // We should have two records with same budgets but different groups, however result for empty query hasn't changed, that means distinct works
    completeBudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class), VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(completeBudgetCollection.getTotalRecords(), equalTo(BUDGET.getInitialQuantity()));
  }
}
