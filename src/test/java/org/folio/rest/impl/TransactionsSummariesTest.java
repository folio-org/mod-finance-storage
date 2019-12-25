package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class TransactionsSummariesTest extends TestBase {

  static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT = "/finance-storage/order-transaction-summaries";
  private static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID = ORDER_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";

  static final String INVOICE_TRANSACTION_SUMMARIES_ENDPOINT = "/finance-storage/invoice-transaction-summaries";
  private static final String INVOICE_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID = INVOICE_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";

  @Test
  void testOrderTransactionSummaries() throws MalformedURLException {
    OrderTransactionSummary sample = new JsonObject(getFile("order_transaction_summary.sample")).mapTo(OrderTransactionSummary.class);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).as(OrderTransactionSummary.class);

    OrderTransactionSummary createdSummary = postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).as(OrderTransactionSummary.class);

    testEntitySuccessfullyFetched(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());

    deleteDataSuccess(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());
  }

  @Test
  void testOrderTransactionSummariesWithValidationError() throws MalformedURLException {
    OrderTransactionSummary sample = new JsonObject(getFile("order_transaction_summary.sample")).mapTo(OrderTransactionSummary.class);
    sample.setNumTransactions(0);
    Error error = postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).then().statusCode(422).contentType(APPLICATION_JSON)
        .extract().as(Errors.class).getErrors().get(0);

   assertThat(error.getParameters().get(0).getKey(), equalTo("numTransactions"));
    assertThat(error.getParameters().get(0).getValue(), equalTo("0"));
  }
}
