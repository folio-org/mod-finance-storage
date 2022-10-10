package org.folio.dao.expense;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class ExpenseClassPostgresDAO implements ExpenseClassDAO{

  public Future<List<ExpenseClass>> getExpenseClasses(String sql, Tuple params, DBClient client) {
    Promise<List<ExpenseClass>> promise = Promise.promise();
    client.getPgClient()
      .select(sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<ExpenseClass> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(ExpenseClass.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

}
