package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.LEDGER;

import java.net.MalformedURLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerFYCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerFiscalYears;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.utils.TestEntities;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class LedgerFYTest extends TestBase {

  private static final String LEDGER_FY_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageLedgerFiscalYears.class);
  private static final int CURRENT_YEAR = LocalDateTime.now().getYear();
  private static final int PAST_YEAR = CURRENT_YEAR - 1;
  private static final int FUTURE_YEAR = CURRENT_YEAR + 1;

  @Test
  public void testGetQueryForOneRecord() throws Exception {
    logger.info("--- Test GET by query when one ledger and one fiscal year are created --- ");

    String fiscalYearId = createFirstRecord(FISCAL_YEAR);
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);

    FiscalYear fiscalYearOne = new JsonObject(getFile(FISCAL_YEAR.getSampleFileName())).mapTo(FiscalYear.class);
    String fiscalYearOneId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYearOne.withCode("FY2017").withCurrency(null));

    String ledgerId = createFirstRecord(LEDGER);
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 1);

    // search with fields from "FY"
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=fiscalYear.code==FY2020", 1);
    // search with fields from "ledgers"
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=ledger.ledgerStatus==Active", 1);

    // search with invalid cql query
    testInvalidCQLQuery(LEDGER_FY_ENDPOINT + "?query=invalid-query");

    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), fiscalYearId);
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);

    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), fiscalYearOneId);
  }

  @Test
  public void testGetQueryForSeveralRecords() throws Exception {
    logger.info("--- Test GET by query when several ledger and fiscal year records are created --- ");
    FiscalYear fiscalYearOne = new JsonObject(getFile(FISCAL_YEAR.getSampleFileName())).mapTo(FiscalYear.class);
    String fiscalYearOneId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYearOne.withCode("FY2017").withCurrency(null));

    Ledger ledger = new JsonObject(getFile(LEDGER.getPathToSampleFile())).mapTo(Ledger.class).withId(null);
    List<String> ledgerIds = new ArrayList<>();

    // Create first ledger (code and name must be unique)
    ledgerIds.add(createEntity(LEDGER.getEndpoint(), ledger.withCode("first").withName(ledger.getCode()).withFiscalYearOneId(fiscalYearOneId)));
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);
    // Create second ledger (code and name must be unique)
    ledgerIds.add(createEntity(LEDGER.getEndpoint(), ledger.withCode("second").withName(ledger.getCode()).withFiscalYearOneId(fiscalYearOneId)));
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);

    FiscalYear fiscalYear = prepareFiscalYear();

    List<String> fiscalYearIds = new ArrayList<>();
    // Create first fiscal year (code must be unique)
    fiscalYearIds.add(createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear.withCurrency("USD").withCode("FY2019")));
    // Check that 2 ledger-FY records are created
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 2);

    // Create second fiscal year (code must be unique)
    fiscalYearIds.add(createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear.withCurrency("BYN").withCode("FY2020")));
    // Check that 2 more ledger-FY records are created
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 4);

    // search from "FY" by currency
    String currency = "BYN";
    LedgerFYCollection ledgerFYs = verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=fiscalYear.currency==" + currency, 2)
      .extract()
      .as(LedgerFYCollection.class);
    // Check that currency is the same as in fiscal year
    ledgerFYs.getLedgerFY().forEach(ledgerFy -> MatcherAssert.assertThat(ledgerFy.getCurrency(), IsEqual.equalTo(currency)));

    // search from "FY" by period end
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=fiscalYear.periodEnd > " + dateInDaysFromNow(2).toInstant(), 4);

    // search with fields from "ledgers"
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=ledger.name==first", 2);

    fiscalYearIds.forEach(fiscalYearId -> {
      try {
        deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), fiscalYearId);
      } catch (MalformedURLException e) {
        Assertions.fail("Cannot delete fiscal year");
      }
    });

    // Make sure that no ledger-FY records left
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);

    ledgerIds.forEach(ledgerId -> {
      try {
        deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
      } catch (MalformedURLException e) {
        Assertions.fail("Cannot delete ledger");
      }
    });

    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), fiscalYearOneId);
  }

  @Test
  public void testGetNoRecordsForFiscalYearWithoutCurrency() throws Exception {
    logger.info("--- Test that GET ledger/fiscal year finds nothing when fiscal year is in past ---");
    FiscalYear fiscalYearOne = new JsonObject(getFile(FISCAL_YEAR.getSampleFileName())).mapTo(FiscalYear.class);
    String fiscalYearOneId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYearOne.withCode("FY2017").withCurrency(null));

    String ledgerId = createFirstRecord(LEDGER);
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);

    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
    deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), fiscalYearOneId);
  }

  @Test
  public void ledgerFYCreatedUponFYCreationOnlyForFYInSeries() throws Exception {
    logger.info("--- Test that ledgerFY records are created for each FY in the series ---");
    List<String> fiscalYearIds = new ArrayList<>();
    // create first fiscal year
    FiscalYear fiscalYear = prepareFiscalYear(CURRENT_YEAR, "FY").withCode("FY2018");
    String fiscalYearId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear);
    fiscalYearIds.add(fiscalYearId);
    // create a ledger
    Ledger ledger = new JsonObject(getFile(LEDGER.getSampleFileName())).mapTo(Ledger.class).withFiscalYearOneId(fiscalYearId);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger);
    // initial ledgerFY records quantity is 1
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 1);

    // add fiscal years with same series
    fiscalYearIds.add(postFiscalYear(PAST_YEAR, "FY"));
    fiscalYearIds.add(postFiscalYear(CURRENT_YEAR, "FY"));
    fiscalYearIds.add(postFiscalYear(FUTURE_YEAR, "FY"));
    // ledgerFY records quantity should be 4
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 4);

    // add fiscal years with another series
    fiscalYearIds.add(postFiscalYear(PAST_YEAR, "QFY"));
    fiscalYearIds.add(postFiscalYear(CURRENT_YEAR, "QFY"));
    fiscalYearIds.add(postFiscalYear(FUTURE_YEAR, "QFY"));
    // ledgerFY records quantity should remain 4
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 4);

    // delete test data
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
    fiscalYearIds.forEach(id -> {
      try {
        deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), id);
      } catch (MalformedURLException e) {
        Assertions.fail("Cannot delete fiscal year");
      }
    });
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);
  }

  @Test
  public void ledgerFYRecordsCreatedForEachFYInSeriesUponLedgerCreation() throws MalformedURLException {
    logger.info("--- Test that ledgerFY records are created for all FY in the series upon ledger creation ---");
    List<String> fiscalYearIds = new ArrayList<>();
    // add fiscal years
    String fiscalYearOneId = postFiscalYear(PAST_YEAR, "FY");
    fiscalYearIds.add(fiscalYearOneId);
    fiscalYearIds.add(postFiscalYear(CURRENT_YEAR, "FY"));
    fiscalYearIds.add(postFiscalYear(FUTURE_YEAR, "FY"));
    // initial ledgerFY records quantity is 0
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);
    // create a ledger
    Ledger ledger = new JsonObject(getFile(LEDGER.getSampleFileName())).mapTo(Ledger.class).withFiscalYearOneId(fiscalYearOneId);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger);

    // expected ledgerFY records quantity is 3
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 3);

    // delete test data
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
    fiscalYearIds.forEach(id -> {
      try {
        deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), id);
      } catch (MalformedURLException e) {
        Assertions.fail("Cannot delete fiscal year");
      }
    });
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);
  }

  @Test
  public void ledgerFYRecordsNotCreatedForFYNotInSeriesUponLedgerCreation() throws MalformedURLException {
    logger.info("--- Test that ledgerFY records aren't created for FY not in the series ---");
    List<String> fiscalYearIds = new ArrayList<>();
    // create first fiscal year
    FiscalYear fiscalYear = prepareFiscalYear(CURRENT_YEAR, "FY").withCode("FY2018");
    String fiscalYearOneId = createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear);
    fiscalYearIds.add(fiscalYearOneId);
    // add fiscal years with another series
    fiscalYearIds.add(postFiscalYear(PAST_YEAR, "QFY"));
    fiscalYearIds.add(postFiscalYear(CURRENT_YEAR, "QFY"));
    fiscalYearIds.add(postFiscalYear(FUTURE_YEAR, "QFY"));
    // initial ledgerFY records quantity is 0
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);
    // create a ledger
    Ledger ledger = new JsonObject(getFile(LEDGER.getSampleFileName())).mapTo(Ledger.class).withFiscalYearOneId(fiscalYearOneId);
    String ledgerId = createEntity(LEDGER.getEndpoint(), ledger);
    // as ledgerFY record is created for fiscalYearOne, expected records quantity is 1
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 1);

    // delete test data
    deleteDataSuccess(LEDGER.getEndpointWithId(), ledgerId);
    fiscalYearIds.forEach(id -> {
      try {
        deleteDataSuccess(FISCAL_YEAR.getEndpointWithId(), id);
      } catch (MalformedURLException e) {
        Assertions.fail("Cannot delete fiscal year");
      }
    });
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT, 0);
  }

  @Test
  public void testGetQueryNothingFound() throws Exception {
    logger.info("--- Test that GET ledger/fiscal year finds nothing when searching for non existent records ---");
    verifyCollectionQuantity(LEDGER_FY_ENDPOINT + "?query=ledger.name==NonExistent", 0);
  }

  @Test
  public void testGetByInvalidQuery() throws Exception {
    logger.info("--- Test that GET ledger/fiscal year returns error if query is invalid cql ---");
    testInvalidCQLQuery(LEDGER_FY_ENDPOINT + "?query=invalid-query");
  }

  private String createFirstRecord(TestEntities testEntity) throws MalformedURLException {
    logger.info("--- Verifying database's initial state: no {} records expected ... ", testEntity.name());
    verifyCollectionQuantity(testEntity.getEndpoint(), 0);

    logger.info("--- Creating record {} ... ", testEntity.name());

    if (testEntity == FISCAL_YEAR) {
      return createEntity(testEntity.getEndpoint(), prepareFiscalYear());
    }

    return postData(testEntity.getEndpoint(), getFile(testEntity.getPathToSampleFile())).then()
      .extract()
      .path("id");
  }

  private FiscalYear prepareFiscalYear() {
    return new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class).withId(null)
      .withCurrency("USD")
      .withPeriodStart(new Date())
      .withPeriodEnd(dateInDaysFromNow(10));
  }

  private FiscalYear prepareFiscalYear(int year, String series) {
    return prepareFiscalYear()
      .withCode(series + year)
      .withPeriodStart(Date.from(LocalDateTime.of(year, 1, 1, 0, 0, 1).toInstant(ZoneOffset.UTC)))
      .withPeriodEnd(Date.from(LocalDateTime.of(year, 12, 31, 23, 59, 59).toInstant(ZoneOffset.UTC)))
      .withSeries(series);
  }

  private String postFiscalYear(int year, String series) throws MalformedURLException {
    return createEntity(FISCAL_YEAR.getEndpoint(), prepareFiscalYear(year, series));
  }

  private Date dateInDaysFromNow(int i) {
    return Date.from(Instant.now().plus(i, ChronoUnit.DAYS));
  }
}
