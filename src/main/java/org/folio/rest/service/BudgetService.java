package org.folio.rest.service;

import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class BudgetService {

  private static final String BUDGET_TABLE = "budget";
  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";
  private static final String TRANSACTIONS_TABLE = "transaction";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private PostgresClient pgClient;

  public BudgetService(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }

  public void deleteById(String id, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
      vertxContext.runOnContext(v -> {
        Tx<String> tx = new Tx<>(id, pgClient);
        getBudgetById(id)
          .compose(budget -> tx.startTx()
            .compose(stringTx -> checkTransactions(budget, tx))
            .compose(stringTx -> unlinkGroupFundFiscalYears(budget, stringTx)))
          .compose(Tx::endTx)
            .setHandler(handleNoContentResponse(asyncResultHandler, tx, "Budget {} {} deleted"));
      });
  }

  private Future<Budget> getBudgetById(String id) {
    Promise<Budget> promise = Promise.promise();

    logger.debug("Get budget={}", id);

    pgClient.getById(BUDGET_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("Budget retrieval with id={} failed", reply.cause(), id);
        handleFailure(promise, reply);
      } else {
        final JsonObject budget = reply.result();
        if (budget == null) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          logger.debug("Budget with id={} successfully extracted", id);
          promise.complete(budget.mapTo(Budget.class));
        }
      }
    });
    return promise.future();
  }

  private Future<Tx<String>> checkTransactions(Budget budget, Tx<String> stringTx) {
    return null;
  }

  private Future<Tx<String>> unlinkGroupFundFiscalYears(Budget budget, Tx<String> stringTx) {
    return null;
  }
}
