package org.folio.rest.impl;

import io.vertx.junit5.VertxExtension;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactionTotals;
import org.folio.rest.persist.HelperUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

import static org.folio.rest.impl.TransactionTest.BATCH_TRANSACTION_ENDPOINT;
import static org.folio.rest.impl.TransactionTest.TRANSACTION_TENANT_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith(VertxExtension.class)
public class TransactionTotalApiTest extends TestBase {

  private static final String TRANSACTION_TOTALS_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageTransactionTotals.class);

  private static TenantJob tenantJob;

  @BeforeEach
  void prepareData() {
    tenantJob = prepareTenant(TRANSACTION_TENANT_HEADER, false, true);
  }

  @AfterEach
  void deleteData() {
    purge(TRANSACTION_TENANT_HEADER);
  }

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, TRANSACTION_TENANT_HEADER);
  }

  @Test
  void getFinanceStorageTransactionTotals() {
    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND,  FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    var initialAllocation = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withCurrency("USD")
      .withToFundId(FUND.getId())
      .withTransactionType(Transaction.TransactionType.ALLOCATION)
      .withAmount(50000.0)
      .withFiscalYearId(FISCAL_YEAR.getId())
      .withSource(Transaction.Source.USER);
    var increaseAllocation = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withCurrency("USD")
      .withToFundId(FUND.getId())
      .withTransactionType(Transaction.TransactionType.ALLOCATION)
      .withAmount(20000.0)
      .withFiscalYearId(FISCAL_YEAR.getId())
      .withSource(Transaction.Source.USER);
    var decreaseAllocation = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withCurrency("USD")
      .withFromFundId(FUND.getId())
      .withTransactionType(Transaction.TransactionType.ALLOCATION)
      .withAmount(5000.0)
      .withFiscalYearId(FISCAL_YEAR.getId())
      .withSource(Transaction.Source.USER);

    var batch = new Batch()
      .withTransactionsToCreate(List.of(initialAllocation, increaseAllocation, decreaseAllocation));
    postData(BATCH_TRANSACTION_ENDPOINT, valueAsString(batch), TRANSACTION_TENANT_HEADER)
      .then().statusCode(204);

    // We expect 1 entry: 20000 (50000 is intentionally ignored)
    var toFundQuery = String.format("?query=(fiscalYearId==%s AND transactionType==(Allocation OR Transfer OR Rollover transfer)) AND toFundId==%s", FISCAL_YEAR.getId(), FUND.getId());
    getData(TRANSACTION_TOTALS_ENDPOINT + toFundQuery, TRANSACTION_TENANT_HEADER)
      .then()
      .log().all()
      .statusCode(200)
      .body("transactionTotals[0].amount", equalTo(20000.0F))
      .body("totalRecords", equalTo(1));

    // We expect 1 entry: -5000
    var fromFundQuery = String.format("?query=(fiscalYearId==%s AND transactionType==(Allocation OR Transfer OR Rollover transfer)) AND fromFundId==%s", FISCAL_YEAR.getId(), FUND.getId());
    getData(TRANSACTION_TOTALS_ENDPOINT + fromFundQuery, TRANSACTION_TENANT_HEADER)
      .then()
      .log().all()
      .statusCode(200)
      .body("transactionTotals[0].amount", equalTo(5000.0F))
      .body("totalRecords", equalTo(1));
  }
}
