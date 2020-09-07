package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class TransactionsSummariesTest extends TestBase {

  static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT = "/finance-storage/order-transaction-summaries";
  static final String ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID = ORDER_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";
  public static final String ORDERS_SUMMARY_SAMPLE = "data/order-transaction-summaries/order-306857_transaction-summary.json";
  public static final String INVOICE_SUMMARY_SAMPLE = "data/invoice-transaction-summaries/invoice-transaction-summary.json";

  static final String INVOICE_TRANSACTION_SUMMARIES_ENDPOINT = "/finance-storage/invoice-transaction-summaries";
  private static final String INVOICE_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID = INVOICE_TRANSACTION_SUMMARIES_ENDPOINT + "/{id}";
  private static final int NUM_TRANSACTIONS = 123;


  @Test
  void testOrderTransactionSummaries() throws MalformedURLException {
    OrderTransactionSummary sample = new JsonObject(getFile(ORDERS_SUMMARY_SAMPLE)).mapTo(OrderTransactionSummary.class);
    postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).as(OrderTransactionSummary.class);

    OrderTransactionSummary createdSummary = postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).as(OrderTransactionSummary.class);

    createdSummary.setNumTransactions(NUM_TRANSACTIONS);
    putData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId(), JsonObject.mapFrom(createdSummary)
      .encodePrettily(), TENANT_HEADER).then()
      .statusCode(204);

    testEntitySuccessfullyFetched(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());
    assertEquals(NUM_TRANSACTIONS, createdSummary.getNumTransactions());

    deleteDataSuccess(ORDER_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());
  }


  @Test
  void testOrderTransactionSummariesWithValidationError() throws MalformedURLException {
    OrderTransactionSummary sample = new JsonObject(getFile(ORDERS_SUMMARY_SAMPLE)).mapTo(OrderTransactionSummary.class);
    sample.setNumTransactions(0);
    Error error = postData(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).then().statusCode(422).contentType(APPLICATION_JSON)
        .extract().as(Errors.class).getErrors().get(0);

   assertThat(error.getParameters().get(0).getKey(), equalTo("numOfTransactions"));
    assertThat(error.getParameters().get(0).getValue(), equalTo("0"));
  }

  @Test
  void testInvoiceTransactionSummaries() throws MalformedURLException {
    InvoiceTransactionSummary sample = new JsonObject(getFile(INVOICE_SUMMARY_SAMPLE)).mapTo(InvoiceTransactionSummary.class);

    InvoiceTransactionSummary createdSummary = postData(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).as(InvoiceTransactionSummary.class);

    testEntitySuccessfullyFetched(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());

    createdSummary.setNumPaymentsCredits(NUM_TRANSACTIONS);
    putData(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId(), JsonObject.mapFrom(createdSummary)
      .encodePrettily(), TENANT_HEADER).then()
      .statusCode(204);

    testEntitySuccessfullyFetched(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());
    assertEquals(NUM_TRANSACTIONS, createdSummary.getNumPaymentsCredits());


    deleteDataSuccess(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT_WITH_ID, createdSummary.getId());
  }

  @Test
  void testInvoiceTransactionSummariesWithValidationError() throws MalformedURLException {
    InvoiceTransactionSummary sample = new JsonObject(getFile(INVOICE_SUMMARY_SAMPLE)).mapTo(InvoiceTransactionSummary.class);
    sample.setNumPaymentsCredits(0);
    Error error = postData(INVOICE_TRANSACTION_SUMMARIES_ENDPOINT, JsonObject.mapFrom(sample)
      .encodePrettily(), TENANT_HEADER).then().statusCode(422).contentType(APPLICATION_JSON)
        .extract().as(Errors.class).getErrors().get(0);

   assertThat(error.getParameters().get(0).getKey(), equalTo("numOfTransactions"));
    assertThat(error.getParameters().get(0).getValue(), equalTo("0"));

  }
}
