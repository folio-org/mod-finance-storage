package org.folio.service.budget;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.budget.BudgetPostgresDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.folio.service.group.GroupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.service.ServiceTestUtils.createRowSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(VertxExtension.class)
public class BudgetServiceTest {
  private AutoCloseable mockitoMocks;
  private BudgetService budgetService;

  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  private GroupService groupService;
  @Mock
  private DBConn conn;
  @Captor
  private ArgumentCaptor<String> tableNameCaptor;
  @Captor
  protected ArgumentCaptor<List<Budget>> budgetListCaptor;
  @Captor
  protected ArgumentCaptor<Budget> budgetCaptor;


  @BeforeEach
  public void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    BudgetDAO budgetDAO = new BudgetPostgresDAO();
    budgetService = new BudgetService(dbClientFactory, budgetDAO, groupService);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void testCreateBatchBudgets(VertxTestContext testContext) {
    Budget budget1 = new Budget()
      .withId(UUID.randomUUID().toString());
    Budget budget2 = new Budget()
      .withId(UUID.randomUUID().toString());
    List<Budget> budgets = List.of(budget1, budget2);
    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(eq(BUDGET_TABLE), anyList());
    doReturn(succeededFuture(null))
      .when(groupService).updateBudgetIdForGroupFundFiscalYears(any(Budget.class), eq(conn));
    testContext.assertComplete(budgetService.createBatchBudgets(budgets, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          verify(conn, times(1)).saveBatch(tableNameCaptor.capture(), budgetListCaptor.capture());
          verify(groupService, times(2)).updateBudgetIdForGroupFundFiscalYears(
            budgetCaptor.capture(), eq(conn));
          List<String> savedTableNames = tableNameCaptor.getAllValues();
          List<List<Budget>> savedBudgetLists = budgetListCaptor.getAllValues();
          assertEquals(BUDGET_TABLE, savedTableNames.get(0));
          List<Budget> savedBudgets = savedBudgetLists.get(0);
          assertEquals(2, savedBudgets.size());
          assertEquals(budget1.getId(), savedBudgets.get(0).getId());
          assertEquals(budget2.getId(), savedBudgets.get(1).getId());
          List<Budget> savedBudgetsForGffy = budgetCaptor.getAllValues();
          assertEquals(2, savedBudgetsForGffy.size());
          assertEquals(budget1.getId(), savedBudgetsForGffy.get(0).getId());
          assertEquals(budget2.getId(), savedBudgetsForGffy.get(1).getId());
        });
        testContext.completeNow();
      });
  }

}
