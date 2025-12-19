package org.folio.service.rollover;

import static org.folio.service.ServiceTestUtils.createRowDesc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import org.folio.rest.exception.HttpException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.internal.RowDesc;

@ExtendWith(VertxExtension.class)
public class RolloverValidationServiceTest {

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private RolloverValidationService rolloverValidationService;

  @Mock
  private DBConn conn;


  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void shouldValidationSuccessfully(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(UUID.randomUUID().toString())
      .withFromFiscalYearId(UUID.randomUUID().toString())
      .withRolloverType(LedgerFiscalYearRollover.RolloverType.COMMIT);

    when(conn.getTenantId()).thenReturn("test");

    RowDesc rowDesc = createRowDesc("foo");
    Row row = new RowImpl(rowDesc);
    row.addBoolean(false);
    RowSet<Row> rows = new LocalRowSet(1).withRows(List.of(row));
    doReturn(Future.succeededFuture(rows))
      .when(conn).execute(anyString(), any(Tuple.class));

    testContext.assertComplete(rolloverValidationService.checkRolloverExists(rollover, conn)
      .onComplete(event -> {
        testContext.verify(() -> verify(conn, times(1))
          .execute(anyString(), any(Tuple.class)));
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

    when(conn.getTenantId()).thenReturn("test");

    RowDesc rowDesc = createRowDesc("foo");
    Row row = new RowImpl(rowDesc);
    row.addBoolean(true);
    RowSet<Row> rows = new LocalRowSet(1).withRows(List.of(row));
    doReturn(Future.succeededFuture(rows))
      .when(conn).execute(anyString(), any(Tuple.class));

    testContext.assertFailure(rolloverValidationService.checkRolloverExists(rollover, conn)
      .onComplete(event -> {
        HttpException exception = (HttpException) event.cause();
        testContext.verify(() -> {
          assertEquals(exception.getCode() , 409);
          assertEquals(exception.getMessage(), "Not unique pair ledgerId and fromFiscalYearId");
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

    testContext.assertComplete(rolloverValidationService.checkRolloverExists(rollover, conn)
      .onComplete(event -> {
        testContext.verify(() -> verify(conn, never())
          .execute(anyString(), any(Tuple.class)));
        testContext.completeNow();
      }));
  }
}
