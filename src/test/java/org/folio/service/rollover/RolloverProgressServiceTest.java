package org.folio.service.rollover;

import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.ERROR;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.DBClient;
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

  @InjectMocks
  private RolloverProgressService rolloverProgressService;

  @Mock
  private RolloverProgressDAO rolloverProgressDAO;
  @Mock
  private RolloverErrorDAO rolloverErrorDAO;
  @Mock
  private DBClient client;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldUpdateRolloverProgressWithErrorOverallStatusWhenThereAreRolloverErrors(VertxTestContext testContext) {

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress()
      .withOrdersRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.IN_PROGRESS);

    LedgerFiscalYearRolloverError error = new LedgerFiscalYearRolloverError();
    when(rolloverErrorDAO.get(any(), any())).thenReturn(Future.succeededFuture(Collections.singletonList(error)));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, client))
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
      .withOrdersRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.IN_PROGRESS);

    when(rolloverErrorDAO.get(any(), any())).thenReturn(Future.succeededFuture(Collections.emptyList()));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, client))
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
      .withOrdersRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.IN_PROGRESS);

    LedgerFiscalYearRolloverError error = new LedgerFiscalYearRolloverError();
    when(rolloverErrorDAO.get(any(), any())).thenReturn(Future.succeededFuture(Collections.singletonList(error)));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, client))
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
      .withOrdersRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.IN_PROGRESS);

    when(rolloverErrorDAO.get(any(), any())).thenReturn(Future.succeededFuture(Collections.emptyList()));
    when(rolloverProgressDAO.update(refEq(progress), any())).thenReturn(Future.succeededFuture());

    testContext.assertComplete(rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress, client))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertEquals(SUCCESS, progress.getOverallRolloverStatus());
        });

        testContext.completeNow();
      });

  }

}
