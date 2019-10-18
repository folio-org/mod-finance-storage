package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.persist.HelperUtils.getEndpoint;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.GROUP;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.net.MalformedURLException;
import java.util.UUID;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupBudgets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class GroupBudgetTest extends TestBase {
  private static final Logger logger = LoggerFactory.getLogger(GroupBudgetTest.class);

  private static final String TENANT_NAME = "acqunitsearch";
  private static final Header VIEW_SEARCH_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TENANT_NAME);
  private static final String EMPTY_ACQ_UNITS_QUERY = "?query=groups.acqUnitIds==[]&limit=20";
  private static final String QUERY_ACQ_UNIT_BY_ID = "?query=groups.acqUnitIds=%s&limit=20";

  @BeforeAll
  public static void before() throws MalformedURLException {
    logger.info("Create a new tenant loading the sample data");
    prepareTenant(VIEW_SEARCH_TENANT_HEADER, true, true);
  }

  @AfterAll
  public static void after() throws MalformedURLException {
    logger.info("Delete the created \"acqunitsearch\" tenant");
    deleteTenant(VIEW_SEARCH_TENANT_HEADER);
  }

  @Test
  public void testSearchByGroupAcqUnitsIds() throws MalformedURLException {
    Group group = new JsonObject(getFile(GROUP.getSampleFileName())).mapTo(Group.class);
    FiscalYear fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getSampleFileName())).mapTo(FiscalYear.class);
    Budget budget = new JsonObject(getFile(BUDGET.getSampleFileName())).mapTo(Budget.class);
    group.setId(null);
    group.setCode("TEST");
    group.setName("Test");
    String acqUnitId = UUID.randomUUID().toString();
    group.getAcqUnitIds().add(acqUnitId);
    Group responseGroup = postData(GROUP.getEndpoint(), JsonObject.mapFrom(group).encodePrettily(), VIEW_SEARCH_TENANT_HEADER).as(Group.class);

    GroupFundFiscalYear groupFundFiscalYear = new GroupFundFiscalYear()
      .withFiscalYearId(fiscalYear.getId())
      .withFundId(budget.getFundId())
      .withBudgetId(budget.getId())
      .withGroupId(responseGroup.getId());

    BudgetCollection group1BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + EMPTY_ACQ_UNITS_QUERY, VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    BudgetCollection group2BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + String.format(QUERY_ACQ_UNIT_BY_ID, acqUnitId), VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(group1BudgetCollection.getBudgets(), hasSize(GROUP_FUND_FY.getInitialQuantity()));
    assertThat(group2BudgetCollection.getBudgets(), hasSize(0));

    GroupFundFiscalYear responseGroupFundFY = postData(GROUP_FUND_FY.getEndpoint(), JsonObject.mapFrom(groupFundFiscalYear).encodePrettily(), VIEW_SEARCH_TENANT_HEADER).as(GroupFundFiscalYear.class);
    group1BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + EMPTY_ACQ_UNITS_QUERY, VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    group2BudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class) + String.format(QUERY_ACQ_UNIT_BY_ID, acqUnitId), VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(group1BudgetCollection.getBudgets(), hasSize(GROUP_FUND_FY.getInitialQuantity()));
    assertThat(group2BudgetCollection.getBudgets(), hasSize(1));

    BudgetCollection completeBudgetCollection = getData(getEndpoint(FinanceStorageGroupBudgets.class), VIEW_SEARCH_TENANT_HEADER).as(BudgetCollection.class);
    assertThat(completeBudgetCollection.getTotalRecords(), equalTo(BUDGET.getInitialQuantity()));
  }
}
