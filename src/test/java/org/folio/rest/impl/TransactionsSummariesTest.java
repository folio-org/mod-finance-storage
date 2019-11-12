package org.folio.rest.impl;

import java.net.MalformedURLException;

import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class TransactionsSummariesTest extends TestBase {

  private static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT = "/finance-storage/order-transaction-summaries";
  private static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID = ORDER_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";

  @Test
  void testOrderTransactionSummaries() throws MalformedURLException {
    OrderTransactionSummary sample = new JsonObject(getFile("order_transaction_summary.sample")).mapTo(OrderTransactionSummary.class);
    OrderTransactionSummary createdSummary = postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).as(OrderTransactionSummary.class);

    testEntitySuccessfullyFetched(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());

    deleteDataSuccess(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());
  }
}
