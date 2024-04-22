package org.folio.dao.rollover;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.impl.RowDesc;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.helpers.LocalRowDesc;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.folio.dao.rollover.RolloverBudgetDAO.ROLLOVER_BUDGET_TABLE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;


@ExtendWith(VertxExtension.class)
public class RolloverBudgetDAOTest {

  private AutoCloseable mockitoMocks;
  @InjectMocks
  private RolloverBudgetDAO rolloverBudgetDAO;
  @Mock
  private DBConn conn;
  @Mock
  private Criterion criterion;

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  public void shouldGetBudgets(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();

    LedgerFiscalYearRolloverBudget budget = new LedgerFiscalYearRolloverBudget().withId(id);

    Results<LedgerFiscalYearRolloverBudget> results = new Results<>();
    results.setResults(List.of(budget));
    doReturn(Future.succeededFuture(results)).when(conn)
      .get(eq(ROLLOVER_BUDGET_TABLE), eq(LedgerFiscalYearRolloverBudget.class), eq(criterion), anyBoolean());

    testContext.assertComplete(rolloverBudgetDAO.getRolloverBudgets(criterion, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.result(), hasSize(1));
          assertEquals(budget, event.result().get(0));
        });

        testContext.completeNow();
      });
  }

  @Test
  public void shouldUpdateBudgets(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();

    LedgerFiscalYearRolloverBudget budget = new LedgerFiscalYearRolloverBudget().withId(id);
    List<LedgerFiscalYearRolloverBudget> budgets = List.of(budget);

    RowDesc rowDesc = new LocalRowDesc(List.of("foo"));
    Row row = new RowImpl(rowDesc);
    row.addJsonObject(new JsonObject().put("id", id));
    RowSet<Row> rows = new LocalRowSet(1).withRows(List.of(row));
    doReturn(Future.succeededFuture(rows)).when(conn)
      .updateBatch(eq(ROLLOVER_BUDGET_TABLE), anyList());

    testContext.assertComplete(rolloverBudgetDAO.updateBatch(budgets, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.result(), hasSize(1));
          assertEquals(budget.getId(), event.result().get(0).getId());
        });

        testContext.completeNow();
      });
  }

  @Test
  public void shouldNotUpdateBudgetsIfNoFound(VertxTestContext testContext) {
    doReturn(Future.succeededFuture(null)).when(conn)
      .updateBatch(eq(ROLLOVER_BUDGET_TABLE), anyList());

    testContext.assertComplete(rolloverBudgetDAO.updateBatch(Collections.emptyList(), conn))
      .onComplete(event -> {
        testContext.verify(() -> assertThat(event.result(), hasSize(0)));

        testContext.completeNow();
      });
  }

  @Test
  public void shouldDeleteBudget(VertxTestContext testContext) {
    doReturn(Future.succeededFuture()).when(conn)
      .delete(eq(ROLLOVER_BUDGET_TABLE), eq(criterion));

    testContext.assertComplete(rolloverBudgetDAO.deleteByQuery(criterion, conn))
      .onComplete(event -> testContext.completeNow());
  }
}
