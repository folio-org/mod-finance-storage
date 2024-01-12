package org.folio.dao.rollover;

import static org.folio.dao.rollover.LedgerFiscalYearRolloverDAO.LEDGER_FISCAL_YEAR_ROLLOVER_TABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBConn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import java.util.UUID;

@ExtendWith(VertxExtension.class)
public class LedgerFiscalYearRolloverDAOTest {

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;

  @Mock
  private DBConn conn;
  @Mock
  private LedgerFiscalYearRollover rollover;


  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void shouldSetRandomUUIDWhenIdNotProvidedWhenCreateLedgerFiscalYearRollover(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    when(rollover.getId())
      .thenReturn(null, id);
    Mockito.doNothing().when(rollover).setId(anyString());

    doReturn(Future.succeededFuture(id)).when(conn)
      .save(eq(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE), anyString(), eq(rollover));

    testContext.assertComplete(ledgerFiscalYearRolloverDAO.create(rollover, conn))
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
    when(rollover.getId()).thenReturn(id);

    doReturn(Future.succeededFuture(id)).when(conn)
      .save(eq(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE), anyString(), eq(rollover));

    testContext.assertComplete(ledgerFiscalYearRolloverDAO.create(rollover, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(rollover, never()).setId(any());
        });
        testContext.completeNow();
      });
  }

}
