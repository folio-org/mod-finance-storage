package org.folio.rest.dao;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.PostgresClient;

import javax.ws.rs.core.Response;

import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class BudgetDAO {

  public static final String BUDGET_NOT_FOUND_FOR_TRANSACTION = "Budget not found for transaction";
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  public Future<Budget> getBudgetByFundIdAndFiscalYearId(String fiscalYearId, String fundId, PostgresClient client) {
    Promise<Budget> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("fundId", fundId)
      .with("fiscalYearId", fiscalYearId)
      .build();
    client.get(BUDGET_TABLE, Budget.class, criterion, false, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else if (reply.result()
        .getResults()
        .isEmpty()) {
        logger.error(BUDGET_NOT_FOUND_FOR_TRANSACTION);
        promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_NOT_FOUND_FOR_TRANSACTION));
      } else {
        promise.complete(reply.result().getResults().get(0));
      }
    });
    return promise.future();
  }
}
