package org.folio.service.transactions;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.service.budget.BudgetService;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.restriction.TransactionRestrictionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;

@ExtendWith(VertxExtension.class)
public class EncumbranceServiceTest {
  private EncumbranceService encumbranceService;

  private AllOrNothingTransactionService mockAllOrNothingEncumbranceService;

  private AutoCloseable mockitoMocks;
  @Mock
  private RequestContext requestContext;
  @Mock
  private TransactionDAO transactionDAO;
  @Mock
  private TemporaryTransactionDAO temporaryTransactionDAO;
  @Mock
  private TransactionSummaryService transactionSummaryService;
  @Mock
  private TransactionRestrictionService transactionRestrictionService;
  @Mock
  private BudgetService budgetService;
  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  private DBClient dbClient;
  @Mock
  private DBConn conn;

  @BeforeEach
  public void initMocks(){
    mockitoMocks = MockitoAnnotations.openMocks(this);

    mockAllOrNothingEncumbranceService = spy(new AllOrNothingTransactionService(transactionDAO, temporaryTransactionDAO,
      transactionSummaryService, transactionRestrictionService, dbClientFactory));

    encumbranceService = new EncumbranceService(mockAllOrNothingEncumbranceService, transactionDAO, budgetService);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
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

    doReturn("testTenant")
      .when(conn).getTenantId();
    doReturn(succeededFuture(List.of(budget)))
      .when(budgetService).getBudgets(any(String.class), any(Tuple.class), eq(conn));
    doReturn(succeededFuture(1))
      .when(budgetService).updateBatchBudgets(any(Collection.class), eq(conn));

    doReturn(dbClient)
      .when(dbClientFactory).getDbClient(eq(requestContext));
    doReturn(succeededFuture(List.of(tmpTransaction)))
      .when(transactionDAO).getTransactions(any(Criterion.class), eq(conn));
    doReturn(succeededFuture(null))
      .when(transactionDAO).updatePermanentTransactions(any(List.class), eq(conn));

    doReturn(succeededFuture(trSummary))
      .when(transactionSummaryService).getAndCheckTransactionSummary(eq(incomingTransaction), eq(conn));
    doReturn(orderId)
      .when(transactionSummaryService).getSummaryId(eq(incomingTransaction));
    doReturn(succeededFuture(trSummary))
      .when(transactionSummaryService).getTransactionSummaryWithLocking(eq(orderId), eq(conn));
    doReturn(succeededFuture(incomingTransaction))
      .when(temporaryTransactionDAO).createTempTransaction(eq(incomingTransaction), eq(orderId), eq("testTenant"), eq(conn));
    doReturn(succeededFuture(List.of(incomingTransaction)))
      .when(temporaryTransactionDAO).getTempTransactionsBySummaryId(eq(orderId), eq(conn));
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(dbClient).withTrans(any());
    doReturn(1)
      .when(transactionSummaryService).getNumTransactions(eq(trSummary));
    doReturn(succeededFuture(null))
      .when(transactionSummaryService).setTransactionsSummariesProcessed(eq(trSummary), eq(conn));
    doReturn(succeededFuture(1))
      .when(temporaryTransactionDAO).deleteTempTransactions(eq(orderId), eq(conn));

    encumbranceService.updateTransaction(incomingTransaction, requestContext)
      .onComplete(event -> {
        testContext.verify(() -> {
          ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);
          verify(transactionDAO).updatePermanentTransactions(argumentCaptor.capture(), eq(conn));
          List<Transaction> transactions = argumentCaptor.getValue();
          assertEquals(Encumbrance.OrderStatus.CLOSED, transactions.get(0).getEncumbrance().getOrderStatus());
          assertEquals(Encumbrance.Status.RELEASED, transactions.get(0).getEncumbrance().getStatus());
        });
        testContext.completeNow();
      });
  }
}
