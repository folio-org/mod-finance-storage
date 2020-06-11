package org.folio.dao.transactions;

import static org.folio.dao.transactions.EncumbranceDAO.TRANSACTIONS_TABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

@ExtendWith(VertxExtension.class)
class BaseTransactionDAOTest {

  @Mock
  private PostgresClient postgresClient;
  @Mock
  private DBClient client;
  @Mock
  private AsyncResult<SQLConnection> connection;

  private BaseTransactionDAO baseTransactionDAO;

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.initMocks(this);
    baseTransactionDAO = Mockito.mock(
      BaseTransactionDAO.class, Mockito.CALLS_REAL_METHODS);
  }

  @Test
  void getTransactionsWithGenericDatabaseException(Vertx vertx, VertxTestContext testContext) {
    when(client.getPgClient()).thenReturn(postgresClient);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Results<Transaction>>> handler = invocation.getArgument(5);
      handler.handle(Future.failedFuture(new PgException("Test", "Test", "22P02", "Test")));
      return null;
    }).when(postgresClient).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), any(Criterion.class), anyBoolean(), anyBoolean(), any(Handler.class));


    testContext.assertFailure(baseTransactionDAO.getTransactions(new Criterion(), client))
      .onComplete(event -> {
        HttpStatusException exception = (HttpStatusException) event.cause();
        testContext.verify(() -> {
          assertEquals(exception.getStatusCode() , 400);
          assertEquals(exception.getPayload(), "Test");
        });
        testContext.completeNow();
      });
  }

  @Test
  void getTransactionsWithConnection(Vertx vertx, VertxTestContext testContext) {

    when(client.getConnection()).thenReturn(connection);
    when(client.getPgClient()).thenReturn(postgresClient);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Results<Transaction>>> handler = invocation.getArgument(6);
      Results<Transaction> results = new Results<>();
      results.setResults(Collections.emptyList());
      handler.handle(Future.succeededFuture(results));
      return null;
    }).when(postgresClient).get(eq(connection), eq(TRANSACTIONS_TABLE), eq(Transaction.class), any(Criterion.class), anyBoolean(), anyBoolean(), any(Handler.class));

    testContext.assertComplete(baseTransactionDAO.getTransactions(new Criterion(), client))
      .onComplete(event -> {
        List<Transaction> transactions =  event.result();
        testContext.verify(() -> {
          assertThat(transactions , hasSize(0));
        });
        testContext.completeNow();
      });
  }

  @Test
  void saveTransactionsToPermanentTable(Vertx vertx, VertxTestContext testContext) {
    when(client.getPgClient()).thenReturn(postgresClient);
    when(client.getConnection()).thenReturn(connection);
    when(client.getTenantId()).thenReturn("test");

    Mockito.when(baseTransactionDAO.createPermanentTransactionsQuery(anyString()))
      .thenReturn("test.table");

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(3);
      handler.handle(Future.failedFuture(new PgException("Test", "Test", "22P02", "Test")));
      return null;
    }).when(postgresClient).execute(eq(connection), eq("test.table"), any(Tuple.class), any(Handler.class));


    testContext.assertFailure(baseTransactionDAO.saveTransactionsToPermanentTable(UUID.randomUUID().toString(), client))
      .onComplete(event -> {
        HttpStatusException exception = (HttpStatusException) event.cause();
        testContext.verify(() -> {
          assertEquals(exception.getStatusCode() , 400);
          assertEquals(exception.getPayload(), "Test");
        });
        testContext.completeNow();
      });
  }

  @Test
  void updatePermanentTransactionsWithEmptyList() {
    when(client.getPgClient()).thenReturn(postgresClient);
    baseTransactionDAO.updatePermanentTransactions(Collections.emptyList(), client);
    verify(client, never()).getPgClient();
    verify(postgresClient, never()).execute(any(), anyString(), any());
  }
}
