package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleVoidAsyncResult;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class FundAPI implements FinanceStorageFunds {
  public static final String FUND_TABLE = "fund";

  private static final Logger log = LoggerFactory.getLogger(FundAPI.class);

  @Override
  @Validate
  public void getFinanceStorageFunds(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FUND_TABLE, Fund.class, FundCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageFundsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFunds(String lang, Fund entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_TABLE, Fund.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundsById(String id, String lang, Fund fund, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    fund.setId(id);
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
      isFundStatusChanged(fund, client)
        .onComplete(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("Update of the fund record {} has failed", cause, fund.getId());
            HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
          } else if (result.result() == null) {
            asyncResultHandler.handle(succeededFuture(FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.respond404WithTextPlain("Not found")));
          } else if (Boolean.TRUE.equals(result.result())) {
            handleFundStatusUpdate(fund, client, asyncResultHandler);
          } else {
            PgUtil.put(FUND_TABLE, fund, id, okapiHeaders, vertxContext, FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.class, asyncResultHandler);
          }
        })
    );
  }

  private void handleFundStatusUpdate(Fund fund, DBClient client, Handler<AsyncResult<Response>> asyncResultHandler) {
     client.startTx()
      .compose(t -> updateRelatedCurrentFYBudgets(fund, client))
      .compose(v -> updateFund(fund, client))
      .compose(v -> client.endTx())
      .onComplete(handleNoContentResponse(asyncResultHandler, fund.getId(), "Fund {} {} updated"));
  }

  private Future<Boolean> isFundStatusChanged(Fund fund, DBClient client) {
    Promise<Boolean> promise = Promise.promise();
    client.getPgClient().getById(FUND_TABLE, fund.getId(), Fund.class, event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        if (event.result() != null) {
          promise.complete(event.result().getFundStatus() != fund.getFundStatus());
        } else {
          promise.complete(null);
        }
      }
    });
    return promise.future();
  }

  private Future<Void> updateFund(Fund fund, DBClient client) {
    Promise<Void> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("id", fund.getId()).build();

   client.getPgClient().update(client.getConnection(), FUND_TABLE, fund, "jsonb", criterion.toString(), false, event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        log.info("Fund record {} was successfully updated", fund);
        promise.complete();
      }
    });
    return promise.future();
  }

  private Future<Void> updateRelatedCurrentFYBudgets(Fund fund, DBClient client) {
    Promise<Void> promise = Promise.promise();

    String fullBudgetTableName = getFullTableName(client.getTenantId(), BUDGET_TABLE);
    String fullFYTableName = getFullTableName(client.getTenantId(), FISCAL_YEAR_TABLE);

//    JsonArray queryParams = new JsonArray();
//    queryParams.add("\"" + fund.getFundStatus() + "\"");
//    queryParams.add(fund.getId());
    String sql = "UPDATE "+ fullBudgetTableName +" SET jsonb = jsonb_set(jsonb,'{budgetStatus}', $1) " +
      "WHERE((fundId=$2) " +
      "AND (budget.fiscalYearId IN " +
      "(SELECT id FROM " + fullFYTableName + " WHERE  current_date between (jsonb->>'periodStart')::timestamp " +
      "AND (jsonb->>'periodEnd')::timestamp)));";

   client.getPgClient().execute(client.getConnection(), sql,
     Tuple.of(JsonObject.mapFrom(fund.getFundStatus()), UUID.fromString(fund.getId())),
     event -> handleVoidAsyncResult(promise, event));
    return promise.future();
  }
}
