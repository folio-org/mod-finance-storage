package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

import java.util.List;
import java.util.UUID;

public class TransactionTest extends TestBase {

  protected static final String TRANSACTION_TEST_TENANT = "transactiontesttenant";
  protected static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  private static final String BATCH_TRANSACTION_SAMPLE = "data/transactions/batch/batch_with_patch.json";
  protected static final String BATCH_TRANSACTION_ENDPOINT = "/finance-storage/transactions/batch-all-or-nothing";
  private static final String TRANSACTION_ENDPOINT_BY_ID = "/finance-storage/transactions/{id}";
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
  void testBatchTransactionsPatch() {
    String batchAsString = getFile(BATCH_TRANSACTION_SAMPLE);
    postData(BATCH_TRANSACTION_ENDPOINT, batchAsString, TRANSACTION_TENANT_HEADER).then()
      .statusCode(500);
  }

  @Test
  void testUpdateEncumbranceConflict() {
    givenTestData(TRANSACTION_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    String orderId = UUID.randomUUID().toString();
    String orderLineId = UUID.randomUUID().toString();

    String encumbranceId = UUID.randomUUID().toString();
    Transaction encumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(FUND.getId())
      .withTransactionType(Transaction.TransactionType.ENCUMBRANCE)
      .withAmount(10.0)
      .withFiscalYearId(FISCAL_YEAR.getId())
      .withSource(Transaction.Source.PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withOrderType(Encumbrance.OrderType.ONE_TIME)
        .withOrderStatus(Encumbrance.OrderStatus.OPEN)
        .withSourcePurchaseOrderId(orderId)
        .withSourcePoLineId(orderLineId)
        .withInitialAmountEncumbered(10d)
        .withSubscription(false)
        .withReEncumber(false));

    Batch batch1 = new Batch()
      .withTransactionsToCreate(List.of(encumbrance));
    postData(BATCH_TRANSACTION_ENDPOINT, valueAsString(batch1), TRANSACTION_TENANT_HEADER)
      .then().statusCode(204);

    Transaction createdEncumbrance = getDataById(TRANSACTION_ENDPOINT_BY_ID, encumbranceId, TRANSACTION_TENANT_HEADER)
      .as(Transaction.class);

    Transaction encumbrance2 = JsonObject.mapFrom(createdEncumbrance).mapTo(Transaction.class)
      .withAmount(9.0)
      .withVersion(1);
    Batch batch2 = new Batch()
      .withTransactionsToUpdate(List.of(encumbrance2));
    postData(BATCH_TRANSACTION_ENDPOINT, valueAsString(batch2), TRANSACTION_TENANT_HEADER)
      .then().statusCode(204);

    Transaction encumbrance3 = JsonObject.mapFrom(createdEncumbrance).mapTo(Transaction.class)
      .withAmount(8.0);
    Batch batch3 = new Batch()
      .withTransactionsToUpdate(List.of(encumbrance3));
    postData(BATCH_TRANSACTION_ENDPOINT, valueAsString(batch3), TRANSACTION_TENANT_HEADER)
      .then().statusCode(409);
  }

}
