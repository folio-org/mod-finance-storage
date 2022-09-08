package org.folio.service.rollover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.helpers.LocalRowSet;
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
import io.vertx.ext.web.handler.HttpException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.RowDesc;

@ExtendWith(VertxExtension.class)
public class RolloverValidationServiceTest {

  @InjectMocks
  private RolloverValidationService rolloverValidationService;

  @Mock
  private PostgresClient postgresClient;

  @Mock
  private DBClient dbClient;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldValidationSuccessfully(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(UUID.randomUUID().toString())
      .withFromFiscalYearId(UUID.randomUUID().toString())
      .withRolloverType(LedgerFiscalYearRollover.RolloverType.COMMIT);

    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getTenantId()).thenReturn("test");

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(2);
      RowDesc rowDesc = new RowDesc(List.of("foo"));
      Row row = new RowImpl(rowDesc);
      row.addBoolean(false);
      RowSet<Row> rows = new LocalRowSet(1).withRows(List.of(row));

      handler.handle(Future.succeededFuture(rows));
      return null;
    }).when(postgresClient).execute(anyString(), any(Tuple.class), any(Handler.class));

    testContext.assertComplete(rolloverValidationService.checkRolloverExists(rollover, dbClient)
      .onComplete(event -> {
        testContext.verify(() -> verify(postgresClient, times(1)).execute(anyString(), any(Tuple.class), any(Handler.class)));
        testContext.completeNow();
      }));
  }

  @Test
  void shouldValidationFailed(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(UUID.randomUUID().toString())
      .withFromFiscalYearId(UUID.randomUUID().toString())
      .withRolloverType(LedgerFiscalYearRollover.RolloverType.COMMIT);

    when(dbClient.getTenantId()).thenReturn("test");
    when(dbClient.getPgClient()).thenReturn(postgresClient);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(2);
      RowDesc rowDesc = new RowDesc(List.of("foo"));
      Row row = new RowImpl(rowDesc);
      row.addBoolean(true);
      RowSet<Row> rows = new LocalRowSet(1).withRows(List.of(row));

      handler.handle(Future.succeededFuture(rows));
      return null;
    }).when(postgresClient).execute(anyString(), any(Tuple.class), any(Handler.class));

    testContext.assertFailure(rolloverValidationService.checkRolloverExists(rollover, dbClient)
      .onComplete(event -> {
        HttpException exception = (HttpException) event.cause();
        testContext.verify(() -> {
          assertEquals(exception.getStatusCode() , 409);
          assertEquals(exception.getPayload(), "Not unique pair ledgerId and fromFiscalYearId");
        });
        testContext.completeNow();
      }));
  }

  @Test
  void shouldValidationNotCommitTypeSuccessfully(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(UUID.randomUUID().toString())
      .withFromFiscalYearId(UUID.randomUUID().toString())
      .withRolloverType(LedgerFiscalYearRollover.RolloverType.PREVIEW);

    testContext.assertComplete(rolloverValidationService.checkRolloverExists(rollover, dbClient)
      .onComplete(event -> {
        testContext.verify(() -> verify(postgresClient, never()).execute(anyString(), any(Tuple.class), any(Handler.class)));
        testContext.completeNow();
      }));
  }
}
