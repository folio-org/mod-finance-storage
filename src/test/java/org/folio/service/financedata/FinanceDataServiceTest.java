package org.folio.service.financedata;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundTags;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.folio.service.fiscalyear.FiscalYearService;
import org.folio.service.fund.FundService;
import org.folio.service.transactions.batch.BatchTransactionService;
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
  private FiscalYearService fiscalYearService;
  @Mock
  private BatchTransactionService batchTransactionService;
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
    var oldFund = new Fund().withId(collection.getFyFinanceData().getFirst().getFundId())
      .withName("NAME").withCode("CODE").withFundStatus(Fund.FundStatus.ACTIVE)
      .withDescription("Old des");
    var oldBudget = new Budget().withId(collection.getFyFinanceData().getFirst().getBudgetId())
      .withName("NAME")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE);
    var fiscalYear = new FiscalYear()
      .withPeriodStart(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)))
      .withPeriodEnd(Date.from(Instant.now().plus(100, ChronoUnit.DAYS)))
      .withCurrency("USD");
    setupMocks(oldFund, oldBudget, fiscalYear);

    testContext.assertComplete(financeDataService.update(collection, requestContext)
      .onComplete(testContext.succeeding(result -> {
        testContext.verify(() -> {
          verifyFundUpdates(collection);
          verifyBudgetUpdates(collection);
          verifyAllocationCreation(collection);
        });
        testContext.completeNow();
      })));
  }

  @Test
  void shouldFailUpdateWhenFundServiceFails(VertxTestContext testContext) {
    var collection = createTestFinanceDataCollection();
    var expectedError = new RuntimeException("Fund service error");
    var fiscalYear = new FiscalYear()
      .withPeriodStart(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)))
      .withPeriodEnd(Date.from(Instant.now().plus(100, ChronoUnit.DAYS)))
      .withCurrency("USD");

    setupMocksForFailure(expectedError, fiscalYear);

    financeDataService.update(collection, requestContext)
      .onComplete(testContext.failing(error -> {
        testContext.verify(() -> {
          assertEquals("Failed to update funds", error.getMessage());
          verify(budgetService, never()).updateBatchBudgets(any(), any(), anyBoolean());
          verify(fundService, never()).updateFunds(any(), any());
        });
        testContext.completeNow();
      }));
  }

  @Test
  void negative_testCreateAllocationTransactionUsingReflection() throws Exception {
    var data = new FyFinanceData()
      .withFundId(UUID.randomUUID().toString())
      .withBudgetId(UUID.randomUUID().toString())
      .withBudgetAllocationChange(50.0)
      .withFiscalYearId(UUID.randomUUID().toString());
    var fiscalYear = new FiscalYear().withCurrency("USD");

    // Use reflection to access the private method
    var method = FinanceDataService.class.getDeclaredMethod("createAllocationTransaction", FyFinanceData.class, String.class);
    method.setAccessible(true);

    Transaction transaction = (Transaction) method.invoke(financeDataService, data, fiscalYear.getCurrency());
    assertEquals(Transaction.TransactionType.ALLOCATION, transaction.getTransactionType());
    assertEquals(data.getFundId(), transaction.getToFundId());
    assertEquals(fiscalYear.getCurrency(), transaction.getCurrency());
    assertEquals(50.0, transaction.getAmount());
  }

  @Test
  void shouldCreateNewBudgetAndAllocation(VertxTestContext testContext) {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    FyFinanceData financeData = new FyFinanceData()
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withFundCode("CODE CHANGED")
      .withFundName("NAME")
      .withFundStatus("Inactive")
      .withFundDescription("New Description")
      .withFundAcqUnitIds(List.of("unit1"))
      .withBudgetName("NAME")
      .withBudgetStatus("Inactive")
      .withBudgetAllocationChange(1.0)
      .withBudgetAllowableExpenditure(800.0)
      .withBudgetAllowableEncumbrance(700.0)
      .withBudgetAcqUnitIds(List.of("unit1"));
    var collection = new FyFinanceDataCollection()
      .withFyFinanceData(List.of(financeData));
    var oldFund = new Fund().withId(collection.getFyFinanceData().getFirst().getFundId())
      .withName("NAME").withCode("CODE").withFundStatus(Fund.FundStatus.ACTIVE)
      .withDescription("Old des");
    var newBudget = new Budget().withId(UUID.randomUUID().toString())
      .withName("NAME")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE);
    var fiscalYear = new FiscalYear()
      .withPeriodStart(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)))
      .withPeriodEnd(Date.from(Instant.now().plus(100, ChronoUnit.DAYS)))
      .withCurrency("USD");
    setupMocks(oldFund, newBudget, fiscalYear);

    testContext.assertComplete(financeDataService.update(collection, requestContext)
      .onComplete(testContext.succeeding(result -> {
        testContext.verify(() -> {
          verifyFundUpdates(collection);
          verifyBudgetCreation();
          verifyBudgetUpdates(collection);
          verifyAllocationCreation(collection);
        });
        testContext.completeNow();
      })));
  }

  private void setupMocks(Fund oldFund, Budget oldBudget, FiscalYear fiscalYear) {
    List<Budget> createdBudgets = new ArrayList<>();
    when(requestContext.toDBClient()).thenReturn(dbClient);
    when(dbClient.withTrans(any()))
      .thenAnswer(invocation -> {
        Function<DBConn, Future<Void>> function = invocation.getArgument(0);
        return function.apply(dbConn);
      });
    when(fundService.getFundsByIds(any(), any(DBConn.class)))
      .thenReturn(Future.succeededFuture(List.of(oldFund)));
    when(fundService.updateFunds(any(), any(DBConn.class)))
      .thenReturn(Future.succeededFuture());
    when(budgetService.getBudgetsByIds(any(), any(DBConn.class)))
      .thenAnswer(invocation -> {
        if (createdBudgets.isEmpty()) {
          return Future.succeededFuture(List.of(oldBudget));
        } else {
          return Future.succeededFuture(createdBudgets);
        }
      });
    when(budgetService.updateBatchBudgets(any(), any(DBConn.class), anyBoolean()))
      .thenReturn(Future.succeededFuture());
    when(budgetService.createBatchBudgets(any(), any(DBConn.class)))
      .thenReturn(Future.succeededFuture());
    when(budgetService.createBatchBudgets(anyList(), any(DBConn.class)))
      .thenAnswer(invocation -> {
        List<Budget> budgets = invocation.getArgument(0);
        createdBudgets.addAll(budgets.stream().map(b -> mapFrom(b).mapTo(Budget.class)).toList());
        return Future.succeededFuture();
      });
    when(batchTransactionService.processBatch(any(Batch.class), any(DBConn.class), anyMap()))
      .thenReturn(Future.succeededFuture());
    when(fiscalYearService.getFiscalYearById(anyString(), any(DBConn.class)))
      .thenReturn(Future.succeededFuture(fiscalYear));
  }

  private void setupMocksForFailure(RuntimeException expectedError, FiscalYear fiscalYear) {
    when(requestContext.toDBClient()).thenReturn(dbClient);
    when(dbClient.withTrans(any()))
      .thenAnswer(invocation -> {
        Function<DBConn, Future<Void>> function = invocation.getArgument(0);
        return function.apply(dbConn);
      });

    when(fundService.getFundsByIds(any(), any())).thenReturn(Future.failedFuture(expectedError));
    when(budgetService.getBudgetsByIds(any(), any())).thenReturn(Future.succeededFuture());
    when(fiscalYearService.getFiscalYearById(anyString(), any(DBConn.class))).thenReturn(Future.succeededFuture(fiscalYear));
  }

  private void verifyFundUpdates(FyFinanceDataCollection collection) {
    ArgumentCaptor<List<String>> fundIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(fundService).getFundsByIds(fundIdsCaptor.capture(), eq(dbConn));
    assertEquals(collection.getFyFinanceData().getFirst().getFundId(), fundIdsCaptor.getValue().getFirst());

    ArgumentCaptor<List<Fund>> fundCaptor = ArgumentCaptor.forClass(List.class);
    verify(fundService).updateFunds(fundCaptor.capture(), eq(dbConn));
    Fund updatedFund = fundCaptor.getValue().getFirst();

    assertNotEquals("CODE CHANGED", updatedFund.getCode());
    assertNotEquals("NAME CHANGED", updatedFund.getName());

    assertEquals(Fund.FundStatus.INACTIVE, updatedFund.getFundStatus());
    assertEquals("New Description", updatedFund.getDescription());
  }

  private void verifyBudgetCreation() {
    ArgumentCaptor<List<Budget>> budgetCaptor = ArgumentCaptor.forClass(List.class);
    verify(budgetService).createBatchBudgets(budgetCaptor.capture(), eq(dbConn));
    Budget createdBudget = budgetCaptor.getValue().getFirst();

    assertEquals("Active", createdBudget.getBudgetStatus().value());
    assertEquals(0.0, createdBudget.getAllocated());
    assertNull(createdBudget.getAllowableExpenditure());
    assertNull(createdBudget.getAllowableEncumbrance());
  }

  private void verifyBudgetUpdates(FyFinanceDataCollection collection) {
    ArgumentCaptor<List<String>> budgetIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(budgetService).getBudgetsByIds(budgetIdsCaptor.capture(), eq(dbConn));
    assertEquals(collection.getFyFinanceData().getFirst().getBudgetId(), budgetIdsCaptor.getValue().getFirst());

    ArgumentCaptor<List<Budget>> budgetCaptor = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetCaptor.capture(), eq(dbConn), anyBoolean());
    Budget updatedBudget = budgetCaptor.getValue().getFirst();

    assertNotEquals("NAME CHANGED", updatedBudget.getName());
    assertNotEquals(1000.0, updatedBudget.getInitialAllocation());

    assertEquals("Inactive", updatedBudget.getBudgetStatus().value());
    assertEquals(800.0, updatedBudget.getAllowableExpenditure());
    assertEquals(700.0, updatedBudget.getAllowableEncumbrance());
  }

  private void verifyAllocationCreation(FyFinanceDataCollection collection) {
    ArgumentCaptor<Batch> batchCaptor = ArgumentCaptor.forClass(Batch.class);
    verify(batchTransactionService).processBatch(batchCaptor.capture(), eq(dbConn), anyMap());
    Batch batch = batchCaptor.getValue();

    assertEquals(1, batch.getTransactionsToCreate().size());
    Transaction tr = batch.getTransactionsToCreate().getFirst();
    assertEquals(Transaction.TransactionType.ALLOCATION, tr.getTransactionType());
    assertEquals(tr.getAmount(), collection.getFyFinanceData().getFirst().getBudgetAllocationChange());
  }

  private FyFinanceDataCollection createTestFinanceDataCollection() {
    String fiscalYearId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String budgetId = UUID.randomUUID().toString();

    FyFinanceData financeData = new FyFinanceData()
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withBudgetId(budgetId)
      .withFundCode("CODE CHANGED")
      .withFundName("NAME CHANGED")
      .withFundStatus("Inactive")
      .withFundDescription("New Description")
      .withFundAcqUnitIds(List.of("unit1"))
      .withFundTags(new FundTags().withTagList(List.of("Education")))
      .withBudgetName("NAME CHANGED")
      .withBudgetStatus("Inactive")
      .withBudgetInitialAllocation(1000.0)
      .withBudgetCurrentAllocation(5000.0)
      .withBudgetAllocationChange(1.0)
      .withBudgetAllowableExpenditure(800.0)
      .withBudgetAllowableEncumbrance(700.0)
      .withBudgetAcqUnitIds(List.of("unit1"));

    return new FyFinanceDataCollection()
      .withFyFinanceData(List.of(financeData));
  }

}
