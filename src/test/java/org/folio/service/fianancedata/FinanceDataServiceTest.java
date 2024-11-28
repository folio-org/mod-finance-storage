package org.folio.service.fianancedata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.folio.service.financedata.FinanceDataService;
import org.folio.service.fund.FundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
public class FinanceDataServiceTest {

  @Mock
  private FundService fundService;
  @Mock
  private BudgetService budgetService;
  @Mock
  private RequestContext requestContext;
  @Mock
  private DBClient dbClient;
  @Mock
  private DBConn dbConn;

  @InjectMocks
  private FinanceDataService financeDataService2;

  private AutoCloseable mockitoMocks;

  @BeforeEach
  void setUp(Vertx vertx) {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void shouldSuccessfullyUpdateFinanceData(VertxTestContext testContext) {
    FinanceDataService financeDataService = Mockito.spy(financeDataService2);
    // given
    FyFinanceDataCollection collection = createTestFinanceDataCollection();
    Fund fund = new Fund()
      .withId(collection.getFyFinanceData().get(0).getFundId())
      .withCode("OLD-CODE")
      .withName("Old Name");
    Budget budget = new Budget()
      .withId(collection.getFyFinanceData().get(0).getBudgetId())
      .withName("Old Budget Name");

    when(requestContext.toDBClient()).thenReturn(dbClient);
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(dbConn);
    }).when(dbClient).withTrans(any());

    when(fundService.getFundsByIds(any(), any())).thenReturn(Future.succeededFuture(List.of(fund)));
    when(fundService.updateFund(any(), any())).thenReturn(Future.succeededFuture());
    when(budgetService.getBudgetsByIds(any(), any())).thenReturn(Future.succeededFuture(List.of(budget)));
    when(budgetService.updateBatchBudgets(any(), any())).thenReturn(Future.succeededFuture());

    // when & then
    testContext.assertComplete(financeDataService.update(collection, requestContext)
      .onComplete(testContext.succeeding(result -> {
        testContext.verify(() -> {
          // Verify transaction handling
          verify(dbClient).withTrans(any());

          // Verify fund updates
          ArgumentCaptor<List<String>> fundIdsCaptor = ArgumentCaptor.forClass(List.class);
          verify(fundService).getFundsByIds(fundIdsCaptor.capture(), eq(dbConn));
          assertEquals(collection.getFyFinanceData().get(0).getFundId(), fundIdsCaptor.getValue().get(0));

          ArgumentCaptor<Fund> fundCaptor = ArgumentCaptor.forClass(Fund.class);
          verify(fundService).updateFund(fundCaptor.capture(), eq(dbConn));
          Fund updatedFund = fundCaptor.getValue();
          assertEquals("NEW-CODE", updatedFund.getCode());
          assertEquals("New Fund Name", updatedFund.getName());
          assertEquals(Fund.FundStatus.ACTIVE, updatedFund.getFundStatus());

          // Verify budget updates
          ArgumentCaptor<List<String>> budgetIdsCaptor = ArgumentCaptor.forClass(List.class);
          verify(budgetService).getBudgetsByIds(budgetIdsCaptor.capture(), eq(dbConn));
          assertEquals(collection.getFyFinanceData().get(0).getBudgetId(), budgetIdsCaptor.getValue().get(0));

          ArgumentCaptor<List<Budget>> budgetCaptor = ArgumentCaptor.forClass(List.class);
          verify(budgetService).updateBatchBudgets(budgetCaptor.capture(), eq(dbConn));
          Budget updatedBudget = budgetCaptor.getValue().get(0);
          assertEquals("New Budget Name", updatedBudget.getName());
          assertEquals(1000.0, updatedBudget.getInitialAllocation());
          assertEquals(900.0, updatedBudget.getAllocated());
          assertEquals(Budget.BudgetStatus.ACTIVE, updatedBudget.getBudgetStatus());
        });
        testContext.completeNow();
      })));
  }

  @Test
  void shouldFailUpdateWhenFundServiceFails(VertxTestContext testContext) {
    // given
    FyFinanceDataCollection collection = createTestFinanceDataCollection();
    RuntimeException expectedError = new RuntimeException("Fund service error");

    when(requestContext.toDBClient()).thenReturn(dbClient);
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(dbConn);
    }).when(dbClient).withTrans(any());

    when(fundService.getFundsByIds(any(), any())).thenReturn(Future.failedFuture(expectedError));

    // when & then
    financeDataService2.update(collection, requestContext)
      .onComplete(testContext.failing(error -> {
        testContext.verify(() -> {
          assertEquals("Fund service error", error.getMessage());
          verify(budgetService, never()).getBudgetsByIds(any(), any());
          verify(budgetService, never()).updateBatchBudgets(any(), any());
        });
        testContext.completeNow();
      }));
  }

  private FyFinanceDataCollection createTestFinanceDataCollection() {
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    FyFinanceData financeData = new FyFinanceData()
      .withFundId(fundId)
      .withBudgetId(budgetId)
      .withFundCode("NEW-CODE")
      .withFundName("New Fund Name")
      .withFundStatus(FyFinanceData.FundStatus.ACTIVE)
      .withFundDescription("New Description")
      .withFundAcqUnitIds(List.of("unit1"))
      .withBudgetName("New Budget Name")
      .withBudgetStatus(FyFinanceData.BudgetStatus.ACTIVE)
      .withBudgetInitialAllocation(1000.0)
      .withBudgetCurrentAllocation(900.0)
      .withBudgetAllowableExpenditure(800.0)
      .withBudgetAllowableEncumbrance(700.0)
      .withBudgetAcqUnitIds(List.of("unit1"));

    return new FyFinanceDataCollection()
      .withFyFinanceData(List.of(financeData));
  }
}
