package org.folio.dao.transactions;

import static org.folio.dao.transactions.EncumbranceDAO.TRANSACTIONS_TABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Tuple;

@ExtendWith(VertxExtension.class)
public class BaseTransactionDAOTest {

  private AutoCloseable mockitoMocks;

  @Mock
  private Conn conn;
  @Mock
  private DBClient dbClient;

  private BaseTransactionDAO baseTransactionDAO;
  private DBConn dbConn;


  @BeforeEach
  public void initMocks(){
    mockitoMocks = MockitoAnnotations.openMocks(this);
    baseTransactionDAO = Mockito.mock(
      BaseTransactionDAO.class, Mockito.CALLS_REAL_METHODS);
    dbConn = new DBConn(dbClient, conn);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void getTransactionsWithGenericDatabaseException(VertxTestContext testContext) {
    doReturn(Future.failedFuture(new PgException("Test", "Test", "22P02", "Test"))).when(conn)
      .get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), any(Criterion.class));

    testContext.assertFailure(baseTransactionDAO.getTransactions(new Criterion(), dbConn))
      .onComplete(event -> {
        HttpException exception = (HttpException) event.cause();
        testContext.verify(() -> {
          assertEquals(exception.getStatusCode() , 400);
          assertEquals(exception.getPayload(), "Test");
        });
        testContext.completeNow();
      });
  }

  @Test
  void getTransactionsWithConnection(VertxTestContext testContext) {
    Results<Transaction> results = new Results<>();
    results.setResults(Collections.emptyList());
    doReturn(Future.succeededFuture(results)).when(conn)
      .get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), any(Criterion.class));

    testContext.assertComplete(baseTransactionDAO.getTransactions(new Criterion(), dbConn))
      .onComplete(event -> {
        List<Transaction> transactions =  event.result();
        testContext.verify(() -> assertThat(transactions , hasSize(0)));
        testContext.completeNow();
      });
  }

  @Test
  void saveTransactionsToPermanentTable(VertxTestContext testContext) {
    when(dbClient.getTenantId())
      .thenReturn("test");

    Mockito.when(baseTransactionDAO.createPermanentTransactionsQuery(anyString()))
      .thenReturn("test.table");

    doReturn(Future.failedFuture(new PgException("Test", "Test", "22P02", "Test"))).when(conn)
      .execute(eq("test.table"), any(Tuple.class));

    testContext.assertFailure(baseTransactionDAO.saveTransactionsToPermanentTable(UUID.randomUUID().toString(), dbConn))
      .onComplete(event -> {
        HttpException exception = (HttpException) event.cause();
        testContext.verify(() -> {
          assertEquals(exception.getStatusCode() , 400);
          assertEquals(exception.getPayload(), "Test");
        });
        testContext.completeNow();
      });
  }

  @Test
  void updatePermanentTransactionsWithEmptyList() {
    baseTransactionDAO.updatePermanentTransactions(Collections.emptyList(), dbConn);
    verify(conn, never()).execute(anyString());
  }
}
