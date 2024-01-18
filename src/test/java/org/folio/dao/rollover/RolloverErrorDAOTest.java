package org.folio.dao.rollover;

import static org.folio.rest.impl.LedgerRolloverProgressAPI.LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.util.UUID;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.DBConn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class RolloverErrorDAOTest {

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private RolloverProgressDAO rolloverProgressDAO;

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
  void shouldCompletedSuccessfullyWhenCreateLedgerFiscalYearRolloverProgress(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress().withId(id);

    doReturn(Future.succeededFuture(id)).when(conn)
      .save(eq(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE), eq(id), eq(progress));

    testContext.assertComplete(rolloverProgressDAO.create(progress, conn))
      .onComplete(event -> testContext.completeNow());
  }

  @Test
  void shouldCompletedSuccessfullyWhenUpdateLedgerFiscalYearRolloverProgress(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress().withId(id);

    doReturn(Future.succeededFuture()).when(conn)
      .update(eq(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE), eq(progress), eq(id));

    testContext.assertComplete(rolloverProgressDAO.update(progress, conn))
      .onComplete(event -> testContext.completeNow());
  }

}
