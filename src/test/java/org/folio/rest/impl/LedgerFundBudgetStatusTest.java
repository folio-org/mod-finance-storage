package org.folio.rest.impl;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.folio.rest.jaxrs.model.Fund.FundStatus.FROZEN;
import static org.folio.rest.jaxrs.model.Fund.FundStatus.INACTIVE;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

import java.net.MalformedURLException;
import java.time.Instant;
import java.util.Date;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class LedgerFundBudgetStatusTest extends TestBase {

  @Test
  public void updateFundStatusOnlyBudgetsWithCurrentFYUpdatedTest() throws MalformedURLException {
    logger.info("--- Test UPDATE fund status, only budgets with current fiscal year are updated --- ");
    Ledger ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class).withId(null);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger.withCode("first").withName(ledger.getCode()).withFiscalYearOneId(FISCAL_YEAR_ONE_ID));

    Fund fund = new JsonObject(getFile(FUND.getPathToSampleFile())).mapTo(Fund.class).withLedgerId(ledgerId).withId(null).withFundTypeId(null);
    String fundId = createEntity(FUND.getEndpoint(), fund.withCode("first").withName(fund.getCode()).withFundStatus(Fund.FundStatus.ACTIVE));

    FiscalYear fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class).withId(null);
    fiscalYear.withCode("FY2019").withName(fiscalYear.getCode())
      .withPeriodEnd(new Date(Instant.now().plus(30, DAYS).toEpochMilli()))
      .withPeriodStart(new Date(Instant.now().minus(30, DAYS).toEpochMilli()));
    String currentFiscalYearId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear);

    Budget budget = new JsonObject(getFile(BUDGET.getPathToSampleFile())).mapTo(Budget.class).withId(null);
    String budget1Id = createEntity(BUDGET.getEndpoint(), budget.withBudgetStatus(Budget.BudgetStatus.ACTIVE).withName("current")
      .withFundId(fundId).withFiscalYearId(currentFiscalYearId));

    fiscalYear.withCode("FY2018").withPeriodStart(new Date(Instant.now().minus(60, DAYS).toEpochMilli()))
      .withPeriodEnd(new Date(Instant.now().minus(30, DAYS).toEpochMilli()));
    String previousFiscalYearId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear);
    String budget2Id = createEntity(BUDGET.getEndpoint(), budget.withBudgetStatus(Budget.BudgetStatus.ACTIVE).withName("previous")
      .withFundId(fundId).withFiscalYearId(previousFiscalYearId));

    fiscalYear.withCode("FY2020").withPeriodStart(new Date(Instant.now().plus(30, DAYS).toEpochMilli()))
      .withPeriodEnd(new Date(Instant.now().plus(60, DAYS).toEpochMilli()));
    String nextFiscalYearId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear);
    String budget3Id = createEntity(BUDGET.getEndpoint(), budget.withBudgetStatus(Budget.BudgetStatus.ACTIVE).withName("next")
      .withFundId(fundId).withFiscalYearId(nextFiscalYearId));

    putData(FUND.getEndpointWithId(), fundId, JsonObject.mapFrom(fund.withFundStatus(FROZEN)).encodePrettily());

    BudgetCollection budgetCollection = getData(BUDGET.getEndpoint() + "?query=budgetStatus==Frozen").as(BudgetCollection.class);
    assertThat(budgetCollection.getBudgets(), hasSize(1));
    assertThat(budgetCollection.getBudgets().get(0).getName(), equalTo("current"));

    deleteDataSuccess(BUDGET.getEndpointWithId(), budget1Id);
    deleteDataSuccess(BUDGET.getEndpointWithId(), budget2Id);
    deleteDataSuccess(BUDGET.getEndpointWithId(), budget3Id);

    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), currentFiscalYearId);
    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), previousFiscalYearId);
    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), nextFiscalYearId);

    deleteDataSuccess(FUND.getEndpointWithId(), fundId);
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
  }

  @Test
  public void updateFundStatusRelatedBudgetsNotExistTest() throws MalformedURLException {
    logger.info("--- Test UPDATE fund status, related budgets with current fiscal year are updated --- ");
    Ledger ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class).withId(null);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger.withCode("first").withName(ledger.getCode()));

    Fund fund = new JsonObject(getFile(FUND.getPathToSampleFile())).mapTo(Fund.class).withLedgerId(ledgerId).withId(null).withFundTypeId(null);
    String fundId = createEntity(FUND.getEndpoint(), fund.withCode("first").withName(fund.getCode()).withFundStatus(Fund.FundStatus.ACTIVE));
    fund.setFundStatus(FROZEN);

    putData(FUND.getEndpointWithId(), fundId, JsonObject.mapFrom(fund).encodePrettily()).then().statusCode(204);
    Fund fundFromStorage = getDataById(FUND.getEndpointWithId(), fundId).as(Fund.class);
    assertThat(fundFromStorage.getFundStatus(), is(FROZEN));
    deleteDataSuccess(FUND.getEndpointWithId(), fundId);
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
  }

  @Test
  public void updateLedgerStatusTest() throws MalformedURLException {
    logger.info("--- Test UPDATE ledger status, related funds and budgets with current fiscal year are updated --- ");
    Ledger ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class).withLedgerStatus(Ledger.LedgerStatus.ACTIVE).withId(null);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger.withCode("first").withName(ledger.getCode()));

    Fund fund = new JsonObject(getFile(FUND.getPathToSampleFile())).mapTo(Fund.class)
      .withLedgerId(ledgerId).withId(null)
      .withFundStatus(Fund.FundStatus.ACTIVE)
      .withCode("first").withName("first")
      .withFundTypeId(null);
    String fund1Id = createEntity(FUND.getEndpoint(), fund);

    fund.withCode("second")
      .withName("second")
      .withFundStatus(INACTIVE);
    String fund2Id = createEntity(FUND.getEndpoint(), fund);

    FiscalYear fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class).withId(null);
    fiscalYear.withCode("FY2019").withName(fiscalYear.getCode())
      .withPeriodEnd(new Date(Instant.now().plus(30, DAYS).toEpochMilli()))
      .withPeriodStart(new Date(Instant.now().minus(30, DAYS).toEpochMilli()));
    String currentFiscalYearId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear);

    Budget budget = new JsonObject(getFile(BUDGET.getPathToSampleFile())).mapTo(Budget.class).withFiscalYearId(currentFiscalYearId).withId(null);
    String fund1BudgetId = createEntity(BUDGET.getEndpoint(), budget.withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withName("budget 1").withFundId(fund1Id));

    String fund2BudgetId = createEntity(BUDGET.getEndpoint(), budget.withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withName("budget 2").withFundId(fund2Id));

    //updating ledger status from Active to Inactive
    putData(LEDGER.getEndpointWithId(), ledgerId, JsonObject.mapFrom(ledger.withLedgerStatus(Ledger.LedgerStatus.INACTIVE)).encodePrettily()).then().statusCode(204);
    Ledger ledgerFromDB = getDataById(LEDGER.getEndpointWithId(), ledgerId).as(Ledger.class);
    assertThat(ledgerFromDB.getLedgerStatus(), is(Ledger.LedgerStatus.INACTIVE));

    //fund1 status and related budget status are updated from Active to Inactive
    Fund fund1FromDB = getDataById(FUND.getEndpointWithId(), fund1Id).as(Fund.class);
    assertThat(fund1FromDB.getFundStatus(), is(Fund.FundStatus.INACTIVE));

    Budget fund1BudgetFromDB = getDataById(BUDGET.getEndpointWithId(), fund1BudgetId).as(Budget.class);
    assertThat(fund1BudgetFromDB.getBudgetStatus(), is(Budget.BudgetStatus.INACTIVE));

    // fund2 status already was Inactive, so status didn't changed and related Budgets status wasn't updated
    Fund fund2FromDB = getDataById(FUND.getEndpointWithId(), fund2Id).as(Fund.class);
    assertThat(fund2FromDB.getFundStatus(), is(Fund.FundStatus.INACTIVE));

    Budget fund2BudgetFromDB = getDataById(BUDGET.getEndpointWithId(), fund2BudgetId).as(Budget.class);
    assertThat(fund2BudgetFromDB.getBudgetStatus(), is(Budget.BudgetStatus.ACTIVE));

    deleteDataSuccess(BUDGET.getEndpointWithId(), fund1BudgetId);
    deleteDataSuccess(BUDGET.getEndpointWithId(), fund2BudgetId);

    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), currentFiscalYearId);

    deleteDataSuccess(FUND.getEndpointWithId(), fund1Id);
    deleteDataSuccess(FUND.getEndpointWithId(), fund2Id);

    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
  }


  @Test
  public void updateLedgerStatusWhenThereIsNoRelatedFundsTest() throws MalformedURLException {
    logger.info("--- Test UPDATE ledger status, related funds not exist  --- ");
    Ledger ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class).withLedgerStatus(Ledger.LedgerStatus.ACTIVE).withId(null);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger.withCode("first").withName(ledger.getCode()).withFiscalYearOneId(FISCAL_YEAR_ONE_ID));

    putData(LEDGER.getEndpointWithId(), ledgerId, JsonObject.mapFrom(ledger.withLedgerStatus(Ledger.LedgerStatus.INACTIVE)).encodePrettily()).then().statusCode(204);
    Ledger ledgerFromDB = getDataById(LEDGER.getEndpointWithId(), ledgerId).as(Ledger.class);
    assertThat(ledgerFromDB.getLedgerStatus(), is(Ledger.LedgerStatus.INACTIVE));

    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
  }

}
