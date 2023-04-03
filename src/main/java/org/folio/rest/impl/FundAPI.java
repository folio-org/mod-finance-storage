package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.apache.logging.log4j.Logger;
import io.vertx.sqlclient.Tuple;

public class FundAPI implements FinanceStorageFunds {
  public static final String FUND_TABLE = "fund";

  private static final Logger log = LogManager.getLogger(FundAPI.class);

  @Override
  @Validate
  public void getFinanceStorageFunds(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FUND_TABLE, Fund.class, FundCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageFundsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFunds(Fund entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_TABLE, Fund.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundsById(String id, Fund fund, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    fund.setId(id);
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
      isFundStatusChanged(fund, client)
        .onComplete(result -> {
          if (result.failed()) {
            HttpException cause = (HttpException) result.cause();
            log.error("Update of the fund record {} has failed", fund.getId(), cause);
            HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
          } else if (result.result() == null) {
            asyncResultHandler.handle(succeededFuture(FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.respond404WithTextPlain("Not found")));
          } else if (Boolean.TRUE.equals(result.result())) {
            handleFundStatusUpdate(fund, client).onComplete(asyncResultHandler);
          } else {
            PgUtil.put(FUND_TABLE, fund, id, okapiHeaders, vertxContext, FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.class, asyncResultHandler);
          }
        })
    );
  }

  private Future<Response> handleFundStatusUpdate(Fund fund, DBClient client) {
    return client.withTrans(conn ->
            updateRelatedCurrentFYBudgets(fund, conn)
            .compose(v -> updateFund(fund, conn))
        )
        .transform(result -> handleNoContentResponse(result, fund.getId(), "Fund {} {} updated"));
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

  private Future<Void> updateFund(Fund fund, DBConn conn) {
    return conn.update(FUND_TABLE, fund, fund.getId())
        .onSuccess(x -> log.info("Fund record {} was successfully updated", fund))
        .mapEmpty();
  }

  private Future<Void> updateRelatedCurrentFYBudgets(Fund fund, DBConn conn) {
    String fullBudgetTableName = getFullTableName(conn.getTenantId(), BUDGET_TABLE);
    String fullFYTableName = getFullTableName(conn.getTenantId(), FISCAL_YEAR_TABLE);

    String sql = "UPDATE "+ fullBudgetTableName +" SET jsonb = jsonb_set(jsonb,'{budgetStatus}', $1) " +
      "WHERE((fundId=$2) " +
      "AND (budget.fiscalYearId IN " +
      "(SELECT id FROM " + fullFYTableName + " WHERE  current_date between (jsonb->>'periodStart')::timestamp " +
      "AND (jsonb->>'periodEnd')::timestamp)));";

    return conn.execute(sql, Tuple.of(fund.getFundStatus().value(), UUID.fromString(fund.getId())))
        .mapEmpty();
  }
}
