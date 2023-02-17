package org.folio.service.transactions;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.core.RestClientTest.X_OKAPI_TENANT;
import static org.folio.rest.core.RestClientTest.X_OKAPI_TOKEN;
import static org.folio.rest.core.RestClientTest.X_OKAPI_USER_ID;
import org.folio.rest.persist.PostgresClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.service.budget.BudgetService;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.restriction.TransactionRestrictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;

@ExtendWith(VertxExtension.class)
public class EncumbranceServiceTest {
  private EncumbranceService encumbranceService;

  private AllOrNothingTransactionService mockAllOrNothingEncumbranceService;
  private TransactionDAO mockTransactionDAO;
  private TemporaryTransactionDAO mockTemporaryTransactionDAO;
  private TransactionSummaryService mockTransactionSummaryService;
  private TransactionRestrictionService mockTransactionRestrictionService;
  private BudgetService mockBudgetService;
  private DBClientFactory mockDBClientFactory;

  private RequestContext mockRequestContext;
  private Vertx vertx;

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.openMocks(this);
    vertx = Vertx.vertx();
    Context context = vertx.getOrCreateContext();
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + NetworkUtils.nextFreePort());
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    mockRequestContext = new RequestContext(context, okapiHeaders);

    mockTransactionDAO = mock(TransactionDAO.class);
    mockTemporaryTransactionDAO = mock(TemporaryTransactionDAO.class);
    mockTransactionSummaryService = mock(TransactionSummaryService.class);
    mockTransactionRestrictionService = mock(TransactionRestrictionService.class);
    mockDBClientFactory  = mock(DBClientFactory.class);
    mockAllOrNothingEncumbranceService = spy(new AllOrNothingTransactionService(mockTransactionDAO, mockTemporaryTransactionDAO,
                  mockTransactionSummaryService, mockTransactionRestrictionService, mockDBClientFactory));
    mockBudgetService = mock(BudgetService.class);

    encumbranceService = new EncumbranceService(mockAllOrNothingEncumbranceService, mockTransactionDAO, mockBudgetService);
  }

  @Test
  void shouldUpdateOrdersStatusToClosedIfEncumbranceAlreadyReleased(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction tmpTransaction = new Transaction().withId(transactionId).withAmount(0.0).withCurrency("USD")
      .withFromFundId(fundId).withTransactionType(Transaction.TransactionType.ENCUMBRANCE)
      .withEncumbrance(new Encumbrance().withStatus(Encumbrance.Status.RELEASED).withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(10d).withAmountExpended(10d).withOrderStatus(Encumbrance.OrderStatus.OPEN));

    Transaction incomingTransaction = new Transaction().withId(transactionId).withAmount(0.0).withCurrency("USD")
      .withFromFundId(fundId).withTransactionType(Transaction.TransactionType.ENCUMBRANCE)
      .withEncumbrance(new Encumbrance().withStatus(Encumbrance.Status.RELEASED).withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(10d).withAmountExpended(10d).withOrderStatus(Encumbrance.OrderStatus.CLOSED));

    JsonObject trSummary = new JsonObject().put("id", orderId).put("numTransactions", 1);

    Budget budget = new Budget().withId(UUID.randomUUID().toString()).withFundId(fundId);

    DBClient mockDBClient = mock(DBClient.class);
    PostgresClient pgClient = mock(PostgresClient.class);
    doReturn(succeededFuture(mockDBClient)).when(mockDBClient).startTx();
    doReturn(vertx).when(mockDBClient).getVertx();
    doReturn("testTenant").when(mockDBClient).getTenantId();
    doReturn(succeededFuture(List.of(budget))).when(mockBudgetService).getBudgets(any(String.class), any(Tuple.class), eq(mockDBClient));
    doReturn(succeededFuture(1)).when(mockBudgetService).updateBatchBudgets(any(Collection.class), eq(mockDBClient));

    doReturn(mockDBClient).when(mockDBClientFactory).getDbClient(eq(mockRequestContext));
    doReturn(succeededFuture(List.of(tmpTransaction))).when(mockTransactionDAO).getTransactions(any(Criterion.class), any(DBClient.class));
    doReturn(succeededFuture(null)).when(mockTransactionDAO).updatePermanentTransactions(any(List.class), eq(mockDBClient));

    doReturn(succeededFuture(trSummary)).when(mockTransactionSummaryService).getAndCheckTransactionSummary(eq(incomingTransaction), any(DBClient.class));
    doReturn(orderId).when(mockTransactionSummaryService).getSummaryId(eq(incomingTransaction));
    doReturn(pgClient).when(mockDBClient).getPgClient();
    doReturn(succeededFuture(List.of(incomingTransaction))).when(pgClient).withTrans(any());
    doReturn(1).when(mockTransactionSummaryService).getNumTransactions(eq(trSummary));
    doReturn(succeededFuture(null)).when(mockTransactionSummaryService).setTransactionsSummariesProcessed(eq(trSummary), any(DBClient.class));
    doReturn(succeededFuture(1)).when(mockTemporaryTransactionDAO).deleteTempTransactions(eq(orderId), any(DBClient.class));

    encumbranceService.updateTransaction(incomingTransaction, mockRequestContext)
      .onComplete(event -> {
        testContext.verify(() -> {
          ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);
          verify(mockTransactionDAO).updatePermanentTransactions(argumentCaptor.capture(), eq(mockDBClient));
          List<Transaction> transactions = argumentCaptor.getValue();
          assertEquals(Encumbrance.OrderStatus.CLOSED, transactions.get(0).getEncumbrance().getOrderStatus());
          assertEquals(Encumbrance.Status.RELEASED, transactions.get(0).getEncumbrance().getStatus());
        });
        testContext.completeNow();
      });
  }
}
