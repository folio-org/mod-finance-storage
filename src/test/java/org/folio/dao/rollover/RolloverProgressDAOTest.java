package org.folio.dao.rollover;

import static org.folio.rest.impl.LedgerRolloverProgressAPI.LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@ExtendWith(VertxExtension.class)
public class RolloverProgressDAOTest {

  @InjectMocks
  private RolloverProgressDAO rolloverProgressDAO;

  @Mock
  private DBClient dbClient;

  @Mock
  private PostgresClient postgresClient;

  @Mock
  private AsyncResult<SQLConnection> connectionAsyncResult;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldCompletedSuccessfullyWhenCreateLedgerFiscalYearRolloverProgress(VertxTestContext testContext) {
    String id = UUID.randomUUID()
      .toString();

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress().withId(id);
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getConnection()).thenReturn(connectionAsyncResult);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<String>> handler = invocation.getArgument(4);
      handler.handle(Future.succeededFuture(id));
      return null;
    }).when(postgresClient)
      .save(any(), eq(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE), eq(id), eq(progress), any(Handler.class));

    testContext.assertComplete(rolloverProgressDAO.create(progress, dbClient))
      .onComplete(event -> testContext.completeNow());
  }

  @Test
  void shouldCompletedSuccessfullyWhenUpdateLedgerFiscalYearRolloverProgress(VertxTestContext testContext) {
    String id = UUID.randomUUID()
            .toString();

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress().withId(id);
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getConnection()).thenReturn(connectionAsyncResult);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(3);
      handler.handle(Future.succeededFuture());
      return null;
    }).when(postgresClient)
            .update(eq(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE), eq(progress), eq(id), any(Handler.class));

    testContext.assertComplete(rolloverProgressDAO.update(progress, dbClient))
            .onComplete(event -> testContext.completeNow());
  }

}
