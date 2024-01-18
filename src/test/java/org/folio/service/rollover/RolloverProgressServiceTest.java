package org.folio.service.rollover;

import static org.folio.rest.jaxrs.model.RolloverStatus.ERROR;
import static org.folio.rest.jaxrs.model.RolloverStatus.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.jaxrs.model.RolloverStatus;
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
public class RolloverProgressServiceTest {

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private RolloverProgressService rolloverProgressService;

  @Mock
  private RolloverProgressDAO rolloverProgressDAO;
  @Mock
  private RolloverErrorService rolloverErrorService;
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
  void shouldUpdateRolloverProgressWithErrorOverallStatusWhenThereAreRolloverErrors(VertxTestContext testContext) {

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress()
      .withOrdersRolloverStatus(RolloverStatus.IN_PROGRESS);

    LedgerFiscalYearRolloverError error = new LedgerFiscalYearRolloverError();
    when(rolloverErrorService.getRolloverErrors(any(), any()))
      .thenReturn(Future.succeededFuture(Collections.singletonList(error)));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(ERROR, progress.getOverallRolloverStatus());
        });

        testContext.completeNow();
      });

  }

  @Test
  void shouldUpdateRolloverProgressWithSuccessOverallStatusWhenThereAreNoRolloverErrors(VertxTestContext testContext) {

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress()
      .withOrdersRolloverStatus(RolloverStatus.IN_PROGRESS);

    when(rolloverErrorService.getRolloverErrors(any(), any()))
      .thenReturn(Future.succeededFuture(Collections.emptyList()));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(SUCCESS, progress.getOverallRolloverStatus());
        });

        testContext.completeNow();
      });

  }

  @Test
  void shouldUpdateRolloverProgressWithErrorFinancialStatusWhenThereAreRolloverErrors(VertxTestContext testContext) {

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress()
      .withOrdersRolloverStatus(RolloverStatus.IN_PROGRESS);

    LedgerFiscalYearRolloverError error = new LedgerFiscalYearRolloverError();
    when(rolloverErrorService.getRolloverErrors(any(), any()))
      .thenReturn(Future.succeededFuture(Collections.singletonList(error)));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(ERROR, progress.getOverallRolloverStatus());
        });

        testContext.completeNow();
      });

  }

  @Test
  void shouldUpdateRolloverProgressWithSuccessFinancialStatusWhenThereAreNoRolloverErrors(VertxTestContext testContext) {

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress()
      .withOrdersRolloverStatus(RolloverStatus.IN_PROGRESS);

    when(rolloverErrorService.getRolloverErrors(any(), any()))
      .thenReturn(Future.succeededFuture(Collections.emptyList()));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(SUCCESS, progress.getOverallRolloverStatus());
        });

        testContext.completeNow();
      });

  }

}
