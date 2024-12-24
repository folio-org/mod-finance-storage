package org.folio.service.fianancedata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundTags;
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
  private FinanceDataService financeDataService;

  private AutoCloseable mockitoMocks;

  @BeforeEach
  void setUp() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void shouldSuccessfullyUpdateFinanceData(VertxTestContext testContext) {
    var collection = createTestFinanceDataCollection();
    var oldFund = new Fund().withId(collection.getFyFinanceData().get(0).getFundId())
      .withName("NAME").withCode("CODE").withFundStatus(Fund.FundStatus.ACTIVE)
      .withDescription("Old des");
    var oldBudget = new Budget().withId(collection.getFyFinanceData().get(0).getBudgetId())
      .withName("NAME")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE);
    setupMocks(oldFund, oldBudget);

    testContext.assertComplete(financeDataService.update(collection, requestContext)
      .onComplete(testContext.succeeding(result -> {
        testContext.verify(() -> {
          verifyFundUpdates(collection);
          verifyBudgetUpdates(collection);
        });
        testContext.completeNow();
      })));
  }

  @Test
  void shouldFailUpdateWhenFundServiceFails(VertxTestContext testContext) {
    var collection = createTestFinanceDataCollection();
    var expectedError = new RuntimeException("Fund service error");

    setupMocksForFailure(expectedError);

    financeDataService.update(collection, requestContext)
      .onComplete(testContext.failing(error -> {
        testContext.verify(() -> {
          assertEquals("Fund service error", error.getMessage());
          verify(budgetService, never()).updateBatchBudgets(any(), any(), anyBoolean());
          verify(fundService, never()).updateFunds(any(), any());
        });
        testContext.completeNow();
      }));
  }

  private void setupMocks(Fund oldFund, Budget oldBudget) {
    when(requestContext.toDBClient()).thenReturn(dbClient);
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(dbConn);
    }).when(dbClient).withTrans(any());

    when(fundService.getFundsByIds(any(), any())).thenReturn(Future.succeededFuture(List.of(oldFund)));
    when(fundService.updateFunds(any(), any())).thenReturn(Future.succeededFuture());
    when(budgetService.getBudgetsByIds(any(), any())).thenReturn(Future.succeededFuture(List.of(oldBudget)));
    when(budgetService.updateBatchBudgets(any(), any(), anyBoolean())).thenReturn(Future.succeededFuture());
  }

  private void setupMocksForFailure(RuntimeException expectedError) {
    when(requestContext.toDBClient()).thenReturn(dbClient);
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(dbConn);
    }).when(dbClient).withTrans(any());

    when(fundService.getFundsByIds(any(), any())).thenReturn(Future.failedFuture(expectedError));
    when(budgetService.getBudgetsByIds(any(), any())).thenReturn(Future.succeededFuture());
  }

  private void verifyFundUpdates(FyFinanceDataCollection collection) {
    ArgumentCaptor<List<String>> fundIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(fundService).getFundsByIds(fundIdsCaptor.capture(), eq(dbConn));
    assertEquals(collection.getFyFinanceData().get(0).getFundId(), fundIdsCaptor.getValue().get(0));

    ArgumentCaptor<List<Fund>> fundCaptor = ArgumentCaptor.forClass(List.class);
    verify(fundService).updateFunds(fundCaptor.capture(), eq(dbConn));
    Fund updatedFund = fundCaptor.getValue().get(0);

    assertNotEquals("CODE CHANGED", updatedFund.getCode());
    assertNotEquals("NAME CHANGED", updatedFund.getName());

    assertEquals(Fund.FundStatus.ACTIVE, updatedFund.getFundStatus());
    assertEquals("New Description", updatedFund.getDescription());
  }

  private void verifyBudgetUpdates(FyFinanceDataCollection collection) {
    ArgumentCaptor<List<String>> budgetIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(budgetService).getBudgetsByIds(budgetIdsCaptor.capture(), eq(dbConn));
    assertEquals(collection.getFyFinanceData().get(0).getBudgetId(), budgetIdsCaptor.getValue().get(0));

    ArgumentCaptor<List<Budget>> budgetCaptor = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetCaptor.capture(), eq(dbConn), anyBoolean());
    Budget updatedBudget = budgetCaptor.getValue().get(0);

    assertNotEquals("NAME CHANGED", updatedBudget.getName());
    assertNotEquals(1000.0, updatedBudget.getInitialAllocation());

    assertEquals(FyFinanceData.BudgetStatus.INACTIVE.value(), updatedBudget.getBudgetStatus().value());
    assertEquals(800.0, updatedBudget.getAllowableExpenditure());
    assertEquals(700.0, updatedBudget.getAllowableEncumbrance());
  }


  private FyFinanceDataCollection createTestFinanceDataCollection() {
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    FyFinanceData financeData = new FyFinanceData()
      .withFundId(fundId)
      .withBudgetId(budgetId)
      .withFundCode("CODE CHANGED")
      .withFundName("NAME CHANGED")
      .withFundStatus(FyFinanceData.FundStatus.INACTIVE)
      .withFundDescription("New Description")
      .withFundAcqUnitIds(List.of("unit1"))
      .withFundTags(new FundTags().withTagList(List.of("Education")))
      .withBudgetName("NAME CHANGED")
      .withBudgetStatus(FyFinanceData.BudgetStatus.INACTIVE)
      .withBudgetInitialAllocation(1000.0)
      .withBudgetAllowableExpenditure(800.0)
      .withBudgetAllowableEncumbrance(700.0)
      .withBudgetAcqUnitIds(List.of("unit1"));

    return new FyFinanceDataCollection()
      .withFyFinanceData(List.of(financeData));
  }
}
