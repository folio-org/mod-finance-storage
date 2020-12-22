package org.folio.service.rollover;

import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.ERROR;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.NOT_STARTED;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.SUCCESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.DBClient;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetService;
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

    @InjectMocks
    private LedgerRolloverService ledgerRolloverService;

    @Mock
    private LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;

    @Mock
    private RolloverProgressService rolloverProgressService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private PostgresFunctionExecutionService postgresFunctionExecutionService;

    @Mock
    private RestClient orderRolloverRestClient;

    @Mock
    private RequestContext requestContext;

    @Mock
    private DBClient dbClient;

    @BeforeEach
    public void initMocks() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateNewRolloverProgressFirstWhenRolloverLedger(VertxTestContext testContext) {
        LedgerRolloverService spy = Mockito.spy(ledgerRolloverService);
        LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
                .withId(UUID.randomUUID().toString());
        Mockito.doReturn(Future.succeededFuture()).when(spy).rolloverPreparation(any(), isA(LedgerFiscalYearRolloverProgress.class), any());
        Mockito.doReturn(Future.succeededFuture()).when(spy).startRollover(any(), isA(LedgerFiscalYearRolloverProgress.class), any());

        testContext.assertComplete(spy.rolloverLedger(rollover, requestContext))
                .onComplete(event -> {
                    testContext.verify(() -> {
                        ArgumentCaptor<LedgerFiscalYearRolloverProgress> argumentCaptor = ArgumentCaptor.forClass(LedgerFiscalYearRolloverProgress.class);
                        verify(spy).rolloverPreparation(eq(rollover), argumentCaptor.capture(), eq(requestContext));
                        LedgerFiscalYearRolloverProgress progress =  argumentCaptor.getValue();
                        assertEquals(rollover.getId(), progress.getLedgerRolloverId());
                        assertEquals(SUCCESS, progress.getBudgetsClosingRolloverStatus());
                        assertEquals(IN_PROGRESS, progress.getOverallRolloverStatus());
                        assertEquals(NOT_STARTED, progress.getFinancialRolloverStatus());
                        assertEquals(NOT_STARTED, progress.getOrdersRolloverStatus());
                        verify(spy).startRollover(eq(rollover), eq(progress), eq(requestContext));
                    });
                    testContext.completeNow();
                });
    }

    @Test
    void shouldCallCloseBudgetsWhenRolloverPreparationWithNeedCloseBudgetsTrue(VertxTestContext testContext) {
        LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
                .withNeedCloseBudgets(true);

        LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress();

        when(requestContext.toDBClient()).thenReturn(dbClient);
        when(dbClient.startTx()).thenReturn(Future.succeededFuture(dbClient));
        when(dbClient.endTx()).thenReturn(Future.succeededFuture());
        when(ledgerFiscalYearRolloverDAO.create(eq(rollover), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(rolloverProgressService.createRolloverProgress(eq(progress), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(budgetService.closeBudgets(eq(rollover), eq(dbClient))).thenReturn(Future.succeededFuture());

        testContext.assertComplete(ledgerRolloverService.rolloverPreparation(rollover, progress, requestContext))
        .onComplete(event -> {
            testContext.verify(() -> {
                verify(budgetService).closeBudgets(eq(rollover), eq(dbClient));
                verify(dbClient, never()).rollbackTransaction();
            });
            testContext.completeNow();
        });

    }

    @Test
    void shouldNotCallCloseBudgetsWhenRolloverPreparationWithNeedCloseBudgetsFalse(VertxTestContext testContext) {
        LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
                .withNeedCloseBudgets(false);

        LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress();

        when(requestContext.toDBClient()).thenReturn(dbClient);
        when(dbClient.startTx()).thenReturn(Future.succeededFuture(dbClient));
        when(dbClient.endTx()).thenReturn(Future.succeededFuture());
        when(ledgerFiscalYearRolloverDAO.create(eq(rollover), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(rolloverProgressService.createRolloverProgress(eq(progress), eq(dbClient))).thenReturn(Future.succeededFuture());

        testContext.assertComplete(ledgerRolloverService.rolloverPreparation(rollover, progress, requestContext))
        .onComplete(event -> {
            testContext.verify(() -> {
                verify(budgetService, never()).closeBudgets(any(), any());
                verify(dbClient, never()).rollbackTransaction();
            });
            testContext.completeNow();
        });

    }

    @Test
    void shouldRollbackTransactionWhenRolloverPreparationWithExceptionThrownInFuturePipeline(VertxTestContext testContext) {
        LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
                .withNeedCloseBudgets(true);

        LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress();
        Throwable t = new IllegalArgumentException();

        when(requestContext.toDBClient()).thenReturn(dbClient);
        when(dbClient.startTx()).thenReturn(Future.succeededFuture(dbClient));
        when(dbClient.rollbackTransaction()).thenReturn(Future.succeededFuture());

        when(ledgerFiscalYearRolloverDAO.create(eq(rollover), eq(dbClient))).thenReturn(Future.failedFuture(t));


        testContext.assertFailure(ledgerRolloverService.rolloverPreparation(rollover, progress, requestContext))
            .onComplete(event -> {
                testContext.verify(() -> {
                    verify(dbClient).rollbackTransaction();
                    verify(dbClient, never()).endTx();
                });
                testContext.completeNow();
            });

    }

    @Test
    void shouldNotUpdateProgressWithErrorStatusWhenStartRolloverCompletedSuccessfully(VertxTestContext testContext) {
        LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
                .withId(UUID.randomUUID().toString());
        LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);


        when(requestContext.toDBClient()).thenReturn(dbClient);
        when(rolloverProgressService.updateRolloverProgress(eq(initialProgress), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, dbClient)).thenReturn(Future.succeededFuture());
        when(rolloverProgressService.calculateAndUpdateOverallProgressStatus(eq(initialProgress), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(orderRolloverRestClient.postEmptyResponse(eq(rollover), eq(requestContext))).thenReturn(Future.succeededFuture());

        testContext.assertComplete(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext))
        .onComplete(event -> {
            testContext.verify(() -> {
                ArgumentCaptor<LedgerFiscalYearRolloverProgress> argumentCaptor = ArgumentCaptor.forClass(LedgerFiscalYearRolloverProgress.class);
                verify(rolloverProgressService, times(2)).updateRolloverProgress(argumentCaptor.capture(), any());
                List<LedgerFiscalYearRolloverProgress> progressArgs = argumentCaptor.getAllValues();
                assertThat(progressArgs, everyItem(allOf(
                        not(hasProperty("budgetsClosingRolloverStatus", is(ERROR))),
                        not(hasProperty("financialRolloverStatus", is(ERROR))),
                        not(hasProperty("ordersRolloverStatus", is(ERROR))),
                        not(hasProperty("overallRolloverStatus", is(ERROR)))
                )));
            });
            testContext.completeNow();
        });
    }

    @Test
    void shouldUpdateProgressFinancialRolloverStatusWithErrorStatusWhenRunBudgetEncumbrancesRolloverScriptFailed(VertxTestContext testContext) {
        LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
                .withId(UUID.randomUUID().toString());
        LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);
        Exception e = new IllegalArgumentException("Test");
        when(requestContext.toDBClient()).thenReturn(dbClient);
        when(rolloverProgressService.updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(IN_PROGRESS), "id"), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(rolloverProgressService.updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(ERROR).withOverallRolloverStatus(ERROR), "id"), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, dbClient)).thenReturn(Future.failedFuture(e));

        testContext.assertFailure(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext))
                .onComplete(event -> {
                    testContext.verify(() -> {
                        assertThat(event.cause(), is(e));
                        verify(rolloverProgressService, times(2)).updateRolloverProgress(any(), any());

                        verify(orderRolloverRestClient, never()).postEmptyResponse(any(), any());
                    });
                    testContext.completeNow();
                });
    }

    @Test
    void shouldUpdateProgressOrdersRolloverStatusWithErrorStatusWhenRolloverPostEmptyResponseFailed(VertxTestContext testContext) {
        LedgerFiscalYearRollover rollover = new LedgerFiscalYearRollover()
                .withId(UUID.randomUUID().toString());
        LedgerFiscalYearRolloverProgress initialProgress = getInitialProgress(rollover);

        Exception e = new IllegalArgumentException("Test");
        when(requestContext.toDBClient()).thenReturn(dbClient);

        when(rolloverProgressService.updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(IN_PROGRESS), "id"), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(rolloverProgressService.updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(SUCCESS).withOrdersRolloverStatus(IN_PROGRESS), "id"), eq(dbClient))).thenReturn(Future.succeededFuture());
        when(rolloverProgressService.updateRolloverProgress(refEq(getInitialProgress(rollover).withFinancialRolloverStatus(SUCCESS).withOrdersRolloverStatus(ERROR).withOverallRolloverStatus(ERROR), "id"), eq(dbClient))).thenReturn(Future.succeededFuture());


        when(postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, dbClient)).thenReturn(Future.succeededFuture());
        when(orderRolloverRestClient.postEmptyResponse(eq(rollover), eq(requestContext))).thenReturn(Future.failedFuture(e));

        testContext.assertFailure(ledgerRolloverService.startRollover(rollover, initialProgress, requestContext))
                .onComplete(event -> {
                    testContext.verify(() -> {
                        assertThat(event.cause(), is(e));
                        verify(rolloverProgressService, times(3)).updateRolloverProgress(any(), any());
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
