package org.folio.service.rollover;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType.FINANCIAL_ROLLOVER;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType.ORDER_ROLLOVER;
import static org.folio.rest.jaxrs.model.RolloverStatus.ERROR;
import static org.folio.rest.jaxrs.model.RolloverStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.RolloverStatus.NOT_STARTED;
import static org.folio.rest.jaxrs.model.RolloverStatus.SUCCESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.EncumbranceRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetService;
import org.folio.service.email.EmailService;
import org.folio.service.fiscalyear.FiscalYearService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class LedgerRolloverServiceTest {

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private LedgerRolloverService ledgerRolloverService;

  @Mock
  private LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;

  @Mock
  private FiscalYearService fiscalYearService;

  @Mock
  private RolloverProgressService rolloverProgressService;

  @Mock
  private RolloverErrorService rolloverErrorService;

  @Mock
  private RolloverBudgetService rolloverBudgetService;

  @Mock
  private RolloverValidationService rolloverValidationService;

  @Mock
  private BudgetService budgetService;

  @Mock
  private PostgresFunctionExecutionService postgresFunctionExecutionService;

  @Mock
  private RestClient orderRolloverRestClient;

  @Mock
  private EmailService emailService;

  @Mock
  private RequestContext requestContext;

  @Mock
  private DBClient dbClient;

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
  void shouldCreateNewRolloverProgressFirstWhenRolloverLedger(VertxTestContext testContext) {
    LedgerRolloverService spy = Mockito.spy(ledgerRolloverService);
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover().withId(UUID.randomUUID()
      .toString());

    when(requestContext.toDBClient())
      .thenReturn(dbClient);
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(dbClient).withTrans(any());
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(dbClient).withConn(any());
    doReturn(Future.succeededFuture())
      .when(spy)
      .rolloverPreparation(any(), isA(LedgerFiscalYearRolloverProgress.class), eq(conn));
    doReturn(Future.succeededFuture())
      .when(spy)
      .startRollover(any(), isA(LedgerFiscalYearRolloverProgress.class), eq(requestContext), eq(conn));

    testContext.assertComplete(spy.rolloverLedger(rollover, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          ArgumentCaptor<LedgerFiscalYearRolloverProgress> argumentCaptor = ArgumentCaptor
            .forClass(LedgerFiscalYearRolloverProgress.class);
          verify(spy).rolloverPreparation(eq(rollover), argumentCaptor.capture(), eq(conn));
          LedgerFiscalYearRolloverProgress progress = argumentCaptor.getValue();
          assertEquals(rollover.getId(), progress.getLedgerRolloverId());
          assertEquals(SUCCESS, progress.getBudgetsClosingRolloverStatus());
          assertEquals(IN_PROGRESS, progress.getOverallRolloverStatus());
          assertEquals(NOT_STARTED, progress.getFinancialRolloverStatus());
          assertEquals(NOT_STARTED, progress.getOrdersRolloverStatus());
          verify(spy).startRollover(eq(rollover), eq(progress), eq(requestContext), eq(conn));
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldCallCloseBudgetsWhenRolloverPreparationWithNeedCloseBudgetsTrue(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover().withNeedCloseBudgets(true);

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress();

    when(fiscalYearService.populateRolloverWithCurrencyFactor(eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(ledgerFiscalYearRolloverDAO.create(eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverValidationService.checkRolloverExists(eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.createRolloverProgress(eq(progress), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(budgetService.closeBudgets(eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());

    testContext.assertComplete(ledgerRolloverService.rolloverPreparation(rollover, progress, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(budgetService).closeBudgets(eq(rollover), eq(conn));
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldNotCallCloseBudgetsWhenRolloverPreparationWithNeedCloseBudgetsFalse(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover().withNeedCloseBudgets(false);

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress();

    when(fiscalYearService.populateRolloverWithCurrencyFactor(eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(ledgerFiscalYearRolloverDAO.create(eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverValidationService.checkRolloverExists(eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.createRolloverProgress(eq(progress), eq(conn)))
      .thenReturn(Future.succeededFuture());

    testContext.assertComplete(ledgerRolloverService.rolloverPreparation(rollover, progress, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(budgetService, never()).closeBudgets(any(), any());
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldNotUpdateProgressWithErrorStatusWhenStartRolloverCompletedSuccessfully(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover().withId(UUID.randomUUID()
      .toString());
    LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);

    when(rolloverProgressService.updateRolloverProgress(eq(initialProgress), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, conn))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.dropTable(anyString(), anyBoolean(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.getRolloverBudgets(rollover.getId(), conn))
      .thenReturn(Future.succeededFuture(List.of(new LedgerFiscalYearRolloverBudget())));
    when(rolloverBudgetService.updateBatch(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateFinancialProgressStatus(eq(initialProgress), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateOverallProgressStatus(eq(initialProgress), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(orderRolloverRestClient.postEmptyResponse(eq(rollover), eq(requestContext)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.updateRolloverBudgetsExpenseClassTotals(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(emailService.createAndSendEmail(eq(requestContext), eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());

    testContext.assertComplete(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(initialProgress,
              allOf(not(hasProperty("budgetsClosingRolloverStatus", is(ERROR))),
                  not(hasProperty("financialRolloverStatus", is(ERROR))), not(hasProperty("ordersRolloverStatus", is(ERROR))),
                  not(hasProperty("overallRolloverStatus", is(ERROR)))));
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldUpdateProgressFinancialRolloverStatusWithErrorStatusWhenRunBudgetEncumbrancesRolloverScriptFailed(
      VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(UUID.randomUUID().toString());
    LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);
    Exception e = new IllegalArgumentException("Test");
    when(rolloverProgressService
      .updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(IN_PROGRESS), "id"), eq(conn)))
        .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateFinancialProgressStatus(eq(initialProgress), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(ERROR)
      .withOverallRolloverStatus(ERROR), "id"), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverErrorService.createRolloverError(any(), any()))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, conn))
      .thenReturn(Future.failedFuture(e));

    testContext.assertFailure(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.cause(), is(e));
          verify(orderRolloverRestClient, never())
            .postEmptyResponse(any(), any());
          // We can't verify the parameters used with verify because the progress object is modified after the first call.
          verify(rolloverProgressService, times(2))
            .updateRolloverProgress(any(), any());
          verify(rolloverErrorService, times(1))
            .createRolloverError(argThat(error -> error.getErrorType() == FINANCIAL_ROLLOVER), eq(conn));
          verifyNoMoreInteractions(rolloverErrorService);
        });
        testContext.completeNow();
      });
  }

  @Test
  void shouldUpdateProgressOrdersRolloverStatusWithErrorStatusWhenRolloverPostEmptyResponseFailed(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
      .withId(UUID.randomUUID().toString())
      .withLedgerId(UUID.randomUUID().toString())
      .withEncumbrancesRollover(List.of(new EncumbranceRollover()));
    LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);

    Exception e = new IllegalArgumentException("Test");

    when(rolloverProgressService.updateRolloverProgress(refEq(initialProgress.withFinancialRolloverStatus(IN_PROGRESS), "id"), eq(conn)))
        .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateFinancialProgressStatus(refEq(initialProgress
      .withOrdersRolloverStatus(IN_PROGRESS), "id"), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(SUCCESS)
      .withOrdersRolloverStatus(ERROR)
      .withOverallRolloverStatus(ERROR), "id"), eq(conn))).thenReturn(Future.succeededFuture());
    when(rolloverErrorService.createRolloverError(any(), any()))
      .thenReturn(Future.succeededFuture());

    when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, conn))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.dropTable(anyString(), anyBoolean(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.getRolloverBudgets(rollover.getId(), conn))
      .thenReturn(Future.succeededFuture(List.of(new LedgerFiscalYearRolloverBudget())));
    when(rolloverBudgetService.updateBatch(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.updateRolloverBudgetsExpenseClassTotals(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(orderRolloverRestClient.postEmptyResponse(eq(rollover), eq(requestContext))).thenReturn(Future.failedFuture(e));

    testContext.assertFailure(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.cause(), is(e));
          verify(rolloverProgressService, times(2)).updateRolloverProgress(any(), any());
          verify(rolloverErrorService,times(1) )
            .createRolloverError(argThat(error -> error.getErrorType() == ORDER_ROLLOVER), eq(conn));
        });
        testContext.completeNow();
      });
  }

  // Adapted for a PREVIEW type due to the new requirements defined by MODORDERS-1171 and implemented by MODORDERS-1162
  @Test
  void shouldSkipOrderRollover(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover().withId(UUID.randomUUID()
      .toString()).withRolloverType(LedgerFiscalYearRollover.RolloverType.PREVIEW);
    LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);

    when(rolloverProgressService.updateRolloverProgress(
      argThat(progress -> progress.getFinancialRolloverStatus().equals(IN_PROGRESS)), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateFinancialProgressStatus(
      argThat(progress -> progress.getOrdersRolloverStatus().equals(IN_PROGRESS)), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateOverallProgressStatus(
      argThat(progress -> progress.getOrdersRolloverStatus().equals(SUCCESS)), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, conn))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.dropTable(anyString(), anyBoolean(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.getRolloverBudgets(rollover.getId(), conn))
      .thenReturn(Future.succeededFuture(List.of(new LedgerFiscalYearRolloverBudget())));
    when(rolloverBudgetService.updateBatch(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.updateRolloverBudgetsExpenseClassTotals(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(emailService.createAndSendEmail(eq(requestContext), eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());

    testContext.assertComplete(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext, conn))
      .onComplete(event -> {
        testContext.verify(() ->
          assertThat(initialProgress, hasProperty("ordersRolloverStatus", is(SUCCESS))));
        testContext.completeNow();
      });
  }

  @Test
  void shouldSkipOrderRolloverAndClosingBudgetsForPreview(VertxTestContext testContext) {
    LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
      .withId(UUID.randomUUID().toString())
      .withRolloverType(LedgerFiscalYearRollover.RolloverType.PREVIEW);
    LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);

    when(requestContext.toDBClient()).thenReturn(dbClient);
    when(rolloverProgressService.updateRolloverProgress(
      argThat(progress -> progress.getFinancialRolloverStatus().equals(IN_PROGRESS)), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateFinancialProgressStatus(
      argThat(progress -> progress.getOrdersRolloverStatus().equals(IN_PROGRESS)), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverProgressService.calculateAndUpdateOverallProgressStatus(
      argThat(progress -> progress.getOrdersRolloverStatus().equals(SUCCESS)), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, conn))
      .thenReturn(Future.succeededFuture());
    when(postgresFunctionExecutionService.dropTable(anyString(), anyBoolean(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.getRolloverBudgets(rollover.getId(), conn))
      .thenReturn(Future.succeededFuture(List.of(new LedgerFiscalYearRolloverBudget())));
    when(rolloverBudgetService.updateBatch(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(rolloverBudgetService.updateRolloverBudgetsExpenseClassTotals(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(emailService.createAndSendEmail(eq(requestContext), eq(rollover), eq(conn)))
      .thenReturn(Future.succeededFuture());

    testContext.assertComplete(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(orderRolloverRestClient, never()).postEmptyResponse(any(), any());
          verify(budgetService, never()).closeBudgets(any(), any());
        });
        testContext.completeNow();
      });
  }

  private LedgerFiscalYearRolloverProgress getInitialProgress(LedgerFiscalYearRollover rollover) {
    return new LedgerFiscalYearRolloverProgress().withId(UUID.randomUUID()
      .toString())
      .withLedgerRolloverId(rollover.getId())
      .withBudgetsClosingRolloverStatus(SUCCESS)
      .withOverallRolloverStatus(IN_PROGRESS)
      .withMetadata(rollover.getMetadata());
  }
}
