package org.folio.dao.rollover;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class RolloverBudgetDAO {

  public static final String ROLLOVER_BUDGET_TABLE = "ledger_fiscal_year_rollover_budget";

  public Future<List<LedgerFiscalYearRolloverBudget>> getRolloverBudgets(Criterion filter, DBClient client) {
    Promise<List<LedgerFiscalYearRolloverBudget>> promise = Promise.promise();
    client.getPgClient()
      .get(ROLLOVER_BUDGET_TABLE, LedgerFiscalYearRolloverBudget.class, filter, true, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().getResults());
        }
      });
    return promise.future();
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> updateBatch(List<LedgerFiscalYearRolloverBudget> rolloverBudgets, DBClient client) {
    Promise<List<LedgerFiscalYearRolloverBudget>> promise = Promise.promise();

    client.getPgClient()
      .updateBatch(ROLLOVER_BUDGET_TABLE, rolloverBudgets, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<LedgerFiscalYearRolloverBudget> budgets = new ArrayList<>();
          if (Objects.nonNull(reply.result())) {
            reply.result()
              .spliterator()
              .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(LedgerFiscalYearRolloverBudget.class)));
          }
          promise.complete(budgets);
        }
    });
    return promise.future();
  }

  public Future<LedgerFiscalYearRolloverBudget> updateRolloverBudget(LedgerFiscalYearRolloverBudget rolloverBudget, DBClient client) {
    Promise<LedgerFiscalYearRolloverBudget> promise = Promise.promise();
    client.getPgClient().
      update(ROLLOVER_BUDGET_TABLE, JsonObject.mapFrom(rolloverBudget), rolloverBudget.getId(), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(rolloverBudget);
        }
      });
    return promise.future();
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
