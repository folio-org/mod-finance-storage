package org.folio.service.budget;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.dao.budget.BudgetPostgresDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBConn;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(VertxExtension.class)
public class BudgetServiceTest {
  private AutoCloseable mockitoMocks;
  private BudgetService budgetService;

  @Mock
  private DBConn conn;
  @Captor
  protected ArgumentCaptor<List<Object>> saveEntitiesCaptor;


  @BeforeEach
  public void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    BudgetPostgresDAO budgetPostgresDAO = new BudgetPostgresDAO();
    budgetService = new BudgetService(budgetPostgresDAO);
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
    testContext.assertComplete(budgetService.createBatchBudgets(budgets, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertEquals(BUDGET_TABLE, saveTableNames.get(0));
          assertEquals(2, saveEntities.get(0).size());
          Budget b1 = (Budget)(saveEntities.get(0).get(0));
          assertEquals(budget1.getId(), b1.getId());
          Budget b2 = (Budget)(saveEntities.get(0).get(1));
          assertEquals(budget2.getId(), b2.getId());
        });
        testContext.completeNow();
      });
  }

}
