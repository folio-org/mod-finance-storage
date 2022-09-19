package org.folio.dao.rollover;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.impl.RowDesc;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.folio.dao.rollover.RolloverBudgetDAO.ROLLOVER_BUDGET_TABLE;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;


@ExtendWith(VertxExtension.class)
public class RolloverBudgetDAOTest {

  @InjectMocks
  private RolloverBudgetDAO rolloverBudgetDAO;
  @Mock
  private DBClient dbClient;
  @Mock
  private PostgresClient postgresClient;
  @Mock
  private AsyncResult<SQLConnection> connectionAsyncResult;
  @Mock
  private Criterion criterion;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void shouldGetBudgets(VertxTestContext testContext) {
    String id = UUID.randomUUID()
      .toString();

    LedgerFiscalYearRolloverBudget budget = new LedgerFiscalYearRolloverBudget().withId(id);
    when(dbClient.getPgClient()).thenReturn(postgresClient);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Results<LedgerFiscalYearRolloverBudget>>> handler = invocation.getArgument(4);
      Results<LedgerFiscalYearRolloverBudget> results = new Results<>();
      results.setResults(Collections.singletonList(budget));
      handler.handle(Future.succeededFuture(results));
      return null;
    }).when(postgresClient)
      .get(eq(ROLLOVER_BUDGET_TABLE), eq(LedgerFiscalYearRolloverBudget.class), eq(criterion), anyBoolean(), any(Handler.class));

    testContext.assertComplete(rolloverBudgetDAO.getRolloverBudgets(criterion, dbClient))
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
    String id = UUID.randomUUID()
      .toString();

    LedgerFiscalYearRolloverBudget budget = new LedgerFiscalYearRolloverBudget().withId(id);
    List<LedgerFiscalYearRolloverBudget> budgets = List.of(budget);
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getConnection()).thenReturn(connectionAsyncResult);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(2);
      RowDesc rowDesc = new RowDesc(List.of("foo"));
      Row row = new RowImpl(rowDesc);
      row.addJsonObject(new JsonObject().put("id", id));
      RowSet<Row> rows = new LocalRowSet(1).withRows(List.of(row));

      handler.handle(Future.succeededFuture(rows));
      return null;
    }).when(postgresClient)
      .updateBatch(eq(ROLLOVER_BUDGET_TABLE), anyList(), any(Handler.class));

    testContext.assertComplete(rolloverBudgetDAO.updateBatch(budgets, dbClient))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.result(), hasSize(1));
          assertEquals(budget.getId(), event.result().get(0).getId());
        });

        testContext.completeNow();
      });
  }

  @Test
  public void shouldDeleteBudget(VertxTestContext testContext) {
    when(dbClient.getPgClient()).thenReturn(postgresClient);
    when(dbClient.getConnection()).thenReturn(connectionAsyncResult);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<RowSet<Row>>> handler = invocation.getArgument(2);
      handler.handle(Future.succeededFuture());
      return null;
    }).when(postgresClient)
      .delete(eq(ROLLOVER_BUDGET_TABLE), eq(criterion), any(Handler.class));

    testContext.assertComplete(rolloverBudgetDAO.deleteByQuery(criterion, dbClient))
      .onComplete(event -> testContext.completeNow());
  }
}
