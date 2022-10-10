package org.folio.dao.budget;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class BudgetExpenseClassPostgresDAO implements BudgetExpenseClassDAO{

  public Future<List<BudgetExpenseClass>> getBudgetExpenseClasses(String sql, Tuple params, DBClient client) {
    Promise<List<BudgetExpenseClass>> promise = Promise.promise();
    client.getPgClient()
      .select(sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<BudgetExpenseClass> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(BudgetExpenseClass.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

}
