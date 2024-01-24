package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.dao.summary.InvoiceTransactionSummaryDAO;
import org.folio.dao.summary.OrderTransactionSummaryDAO;
import org.folio.dao.transactions.TemporaryInvoiceTransactionDAO;
import org.folio.dao.transactions.TemporaryOrderTransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.rest.jaxrs.model.TransactionPatch;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@ExtendWith(VertxExtension.class)
public class BatchTransactionServiceTest {
  private AutoCloseable mockitoMocks;
  private BatchTransactionService batchTransactionService;
  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  private TransactionManagingStrategyFactory managingServiceFactory;
  @Mock
  private DefaultTransactionService defaultTransactionService;
  @Mock
  private EncumbranceService encumbranceService;
  @Mock
  private PendingPaymentService pendingPaymentService;
  @Mock
  private OrderTransactionSummaryDAO orderTransactionSummaryDAO;
  @Mock
  private InvoiceTransactionSummaryDAO invoiceTransactionSummaryDAO;
  @Mock
  private TemporaryOrderTransactionDAO temporaryOrderTransactionDAO;
  @Mock
  private TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO;
  @Mock
  private RequestContext requestContext;
  @Mock
  private DBClient dbClient;
  @Mock
  private DBConn conn;

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    batchTransactionService = new BatchTransactionService(dbClientFactory, managingServiceFactory, defaultTransactionService,
      orderTransactionSummaryDAO, invoiceTransactionSummaryDAO, temporaryOrderTransactionDAO, temporaryInvoiceTransactionDAO);
    doReturn(dbClient)
      .when(dbClientFactory).getDbClient(eq(requestContext));
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(dbClient).withTrans(any());
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
      .withTransactionType(TransactionType.PENDING_PAYMENT);
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

  @Test
  void testCreateEncumbrance(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();
    String orderLineId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Transaction encumbrance = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(Transaction.TransactionType.ENCUMBRANCE)
      .withAmount(10.0)
      .withFiscalYearId(fiscalYearId)
      .withSource(Transaction.Source.PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withOrderType(Encumbrance.OrderType.ONE_TIME)
        .withOrderStatus(Encumbrance.OrderStatus.OPEN)
        .withSourcePurchaseOrderId(orderId)
        .withSourcePoLineId(orderLineId)
        .withInitialAmountEncumbered(10d)
        .withSubscription(false)
        .withReEncumber(false));
    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(encumbrance);
    doReturn(encumbranceService)
      .when(managingServiceFactory).findStrategy(any());
    doReturn(Future.succeededFuture(null))
      .when(orderTransactionSummaryDAO).getSummaryById(eq(orderId), eq(conn));
    doReturn(Future.succeededFuture())
      .when(orderTransactionSummaryDAO).createSummary(argThat(jsonObject -> orderId.equals(jsonObject.getString("id"))),
        eq(conn));
    doReturn(Future.succeededFuture(encumbrance))
      .when(encumbranceService).createTransaction(eq(encumbrance), eq(conn));
    doReturn(Future.succeededFuture(0))
      .when(temporaryOrderTransactionDAO).deleteTempTransactions(eq(orderId), eq(conn));
    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> testContext.completeNow());
  }

  @Test
  void testUpdatePendingPayment(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";
    Transaction pendingPayment = new Transaction()
      .withId(transactionId)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency)
      .withTransactionType(TransactionType.PENDING_PAYMENT);
    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(pendingPayment);
    InvoiceTransactionSummary invoiceTransactionSummary = new InvoiceTransactionSummary()
      .withId(UUID.randomUUID().toString())
      .withNumPaymentsCredits(1)
      .withNumPendingPayments(1);
    doReturn(pendingPaymentService)
      .when(managingServiceFactory).findStrategy(any());
    doReturn(Future.succeededFuture(JsonObject.mapFrom(invoiceTransactionSummary)))
      .when(invoiceTransactionSummaryDAO).getSummaryById(eq(invoiceId), eq(conn));
    doReturn(Future.succeededFuture())
      .when(invoiceTransactionSummaryDAO).updateSummary(argThat(jsonObject -> invoiceId.equals(jsonObject.getString("id"))),
        eq(conn));
    doReturn(Future.succeededFuture())
      .when(pendingPaymentService).updateTransaction(eq(pendingPayment), eq(conn));
    doReturn(Future.succeededFuture(0))
      .when(temporaryInvoiceTransactionDAO).deleteTempTransactions(eq(invoiceId), eq(conn));
    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> testContext.completeNow());
  }

  @Test
  void testDeleteTransaction(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    Batch batch = new Batch();
    batch.getIdsOfTransactionsToDelete().add(transactionId);
    doReturn(Future.succeededFuture())
      .when(defaultTransactionService).deleteTransactionById(eq(transactionId), eq(conn));
    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> testContext.completeNow());
  }

  @Test
  void testPatchTransaction(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    TransactionPatch transactionPatch = new TransactionPatch()
      .withId(transactionId)
      .withAdditionalProperty("encumbrance", new LinkedHashMap<String, String>()
        .put("orderStatus", Encumbrance.OrderStatus.CLOSED.value()));
    Batch batch = new Batch();
    batch.getTransactionPatches().add(transactionPatch);
    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          assertThat(((HttpException) thrown).getStatusCode(), equalTo(500));
          assertThat(((HttpException) thrown).getPayload(), equalTo("transactionPatches: not implemented"));
        });
        testContext.completeNow();
      });
  }
}
