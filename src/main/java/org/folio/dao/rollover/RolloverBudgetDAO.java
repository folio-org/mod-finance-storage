package org.folio.dao.rollover;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;

public class RolloverBudgetDAO {

  public static final String SELECT_ROLLOVER_BUDGETS_BY_ROLLOVER_ID = "SELECT jsonb FROM %s WHERE ledgerRolloverId = $1";
  public static final String ROLLOVER_BUDGET_TABLE = "ledger_fiscal_year_rollover_budget";

  public Future<List<LedgerFiscalYearRolloverBudget>> getRolloverBudgets(String rolloverId, DBClient client) {
    Promise<List<LedgerFiscalYearRolloverBudget>> promise = Promise.promise();
    client.getPgClient()
      .select(String.format(SELECT_ROLLOVER_BUDGETS_BY_ROLLOVER_ID, getFullTableName(client.getTenantId(), ROLLOVER_BUDGET_TABLE)), Tuple.from(List.of(rolloverId)), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<LedgerFiscalYearRolloverBudget> budgets = new ArrayList<>();
          reply
            .result()
            .spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(LedgerFiscalYearRolloverBudget.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> updateBatch(List<LedgerFiscalYearRolloverBudget> rolloverBudgets, DBClient client) {
    List<LedgerFiscalYearRolloverBudget> budgets = new ArrayList<>();

    return client.getPgClient().updateBatch(ROLLOVER_BUDGET_TABLE, rolloverBudgets)
      .compose( reply -> {
        reply
          .spliterator()
          .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(LedgerFiscalYearRolloverBudget.class)));
        return Future.succeededFuture(budgets);
      });
  }

  public Future<Void> deleteByQuery(Criterion filter, DBClient client) {
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .delete(ROLLOVER_BUDGET_TABLE, filter, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete();
        }
      });
    return promise.future();
  }
}
