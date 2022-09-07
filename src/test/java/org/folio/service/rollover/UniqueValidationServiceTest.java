package org.folio.service.rollover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class UniqueValidationServiceTest {

  @InjectMocks
  private UniqueValidationService uniqueValidationService;

  @Mock
  private LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;

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

    when(dbClient.getTenantId()).thenReturn(UUID.randomUUID().toString());
    when(ledgerFiscalYearRolloverDAO.validationOfUniqueness(anyString(), eq(dbClient))).thenReturn(Future.succeededFuture(true));

    testContext.assertComplete(uniqueValidationService.validationOfUniqueness(rollover, dbClient)
      .onComplete(event -> {
        testContext.verify(() -> verify(ledgerFiscalYearRolloverDAO, times(1)).validationOfUniqueness(anyString(), eq(dbClient)));
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

    when(dbClient.getTenantId()).thenReturn(UUID.randomUUID().toString());
    when(ledgerFiscalYearRolloverDAO.validationOfUniqueness(anyString(), eq(dbClient))).thenReturn(Future.succeededFuture(false));

    testContext.assertFailure(uniqueValidationService.validationOfUniqueness(rollover, dbClient)
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

    testContext.assertComplete(uniqueValidationService.validationOfUniqueness(rollover, dbClient)
      .onComplete(event -> {
        testContext.verify(() -> verify(ledgerFiscalYearRolloverDAO, never()).validationOfUniqueness(anyString(), eq(dbClient)));
        testContext.completeNow();
      }));
  }
}
