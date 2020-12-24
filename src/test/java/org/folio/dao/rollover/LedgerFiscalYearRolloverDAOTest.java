package org.folio.dao.rollover;

import static org.folio.dao.rollover.LedgerFiscalYearRolloverDAO.LEDGER_FISCAL_YEAR_ROLLOVER_TABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import java.util.UUID;

@ExtendWith(VertxExtension.class)
public class LedgerFiscalYearRolloverDAOTest {

  @InjectMocks
  private LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;

  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient postgresClient;
  @Mock
  private LedgerFiscalYearRollover rollover;
  @Mock
  private AsyncResult<SQLConnection> connectionAsyncResult;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldSetRandomUUIDWhenIdNotProvidedWhenCreateLedgerFiscalYearRollover(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getConnection()).thenReturn(connectionAsyncResult);
    when(rollover.getId()).thenReturn(null, id);
    Mockito.doNothing().when(rollover).setId(anyString());

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<String>> handler = invocation.getArgument(4);
      handler.handle(Future.succeededFuture(id));
      return null;
    }).when(postgresClient)
      .save(any(), eq(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE), anyString(), eq(rollover), any(Handler.class));

    testContext.assertComplete(ledgerFiscalYearRolloverDAO.create(rollover, dbClient))
      .onComplete(event -> {

        testContext.verify(() -> {
          verify(rollover).setId(notNull());
        });
        testContext.completeNow();
      });
  }


  @Test
  void shouldNotSetRandomUUIDWhenIdProvidedWhenCreateLedgerFiscalYearRollover(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getConnection()).thenReturn(connectionAsyncResult);
    when(rollover.getId()).thenReturn(id);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<String>> handler = invocation.getArgument(4);
      handler.handle(Future.succeededFuture(id));
      return null;
    }).when(postgresClient)
            .save(any(), eq(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE), anyString(), eq(rollover), any(Handler.class));

    testContext.assertComplete(ledgerFiscalYearRolloverDAO.create(rollover, dbClient))
            .onComplete(event -> {

              testContext.verify(() -> {
                verify(rollover, never()).setId(any());
              });
              testContext.completeNow();
            });
  }

}
