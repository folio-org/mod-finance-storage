package org.folio.service.transactions;

import io.vertx.ext.web.handler.HttpException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

@ExtendWith(VertxExtension.class)
public class BatchTransactionServiceTest {
  private AutoCloseable mockitoMocks;
  @InjectMocks
  private BatchTransactionService batchTransactionService;
  @Mock
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void testBatchEntityValidity(VertxTestContext testContext) {
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";
    Transaction pendingPayment = new Transaction()
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency)
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT);
    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(pendingPayment);
    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          assertThat(((HttpException) thrown).getStatusCode(), equalTo(400));
          assertThat(((HttpException) thrown).getPayload(), equalTo("Id is required in transactions to create."));
        });
        testContext.completeNow();
      });
  }
}
