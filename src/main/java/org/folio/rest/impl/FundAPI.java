package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class FundAPI implements FinanceStorageFunds {
  static final String FUND_TABLE = "fund";

  private static final Logger log = LoggerFactory.getLogger(FundAPI.class);
  private PostgresClient pgClient;
  private String tenantId;

  public FundAPI(String tenantId) {
    this.tenantId = tenantId;
  }

  public FundAPI(Vertx vertx, String tenantId) {
    this(tenantId);
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

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
    vertxContext.runOnContext(event ->
      isFundStatusChanged(fund)
        .setHandler(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("Update of the fund record {} has failed", cause, fund.getId());
            HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
          } else if (result.result() == null) {
            asyncResultHandler.handle(succeededFuture(FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.respond404WithTextPlain("Not found")));
          } else if (Boolean.TRUE.equals(result.result())) {
            handleFundStatusUpdate(fund, asyncResultHandler);
          } else {
            PgUtil.put(FUND_TABLE, fund, id, okapiHeaders, vertxContext, FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.class, asyncResultHandler);
          }
        })
    );
  }

  private void handleFundStatusUpdate(Fund fund, Handler<AsyncResult<Response>> asyncResultHandler) {
    Tx<Fund> tx = new Tx<>(fund, pgClient);

    HelperUtils.startTx(tx)
      .compose(this::updateRelatedCurrentFYBudgets)
      .compose(this::updateFund)
      .compose(HelperUtils::endTx)
      .setHandler(result -> {
        if (result.failed()) {
          HttpStatusException cause = (HttpStatusException) result.cause();
          log.error("Update of the fund record {} has failed", cause, tx.getEntity());

          HelperUtils.rollbackTransaction(tx).setHandler(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
        } else {
          log.info("Fund record {} and associated data were successfully updated", tx.getEntity());
          asyncResultHandler.handle(succeededFuture(FinanceStorageLedgers.DeleteFinanceStorageLedgersByIdResponse.respond204()));
        }
      });
  }

  private Future<Boolean> isFundStatusChanged(Fund fund) {
    Promise<Boolean> promise = Promise.promise();
    pgClient.getById(FUND_TABLE, fund.getId(), Fund.class, event -> {
      if (event.failed()) {
        HelperUtils.handleFailure(promise, event);
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

  private Future<Tx<Fund>> updateFund(Tx<Fund> tx) {
    Promise<Tx<Fund>> promise = Promise.promise();
    Fund fund = tx.getEntity();

    Criterion criterion = HelperUtils.getCriterionByFieldNameAndValue("id", "=", fund.getId());
    tx.getPgClient().update(tx.getConnection(), FUND_TABLE, fund, "jsonb", criterion.toString(), false, event -> {
      if (event.failed()) {
        HelperUtils.handleFailure(promise, event);
      } else {
        log.info("Fund record {} was successfully updated", tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<Tx<Fund>> updateRelatedCurrentFYBudgets(Tx<Fund> fundTx) {
    Promise<Tx<Fund>> promise = Promise.promise();

    Fund fund = fundTx.getEntity();
    String fullBudgetTableName = PostgresClient.convertToPsqlStandard(tenantId) + "." + BUDGET_TABLE;
    String fullFYTableName = PostgresClient.convertToPsqlStandard(tenantId) + "." + FISCAL_YEAR_TABLE;

    JsonArray queryParams = new JsonArray();
    queryParams.add("\"" + fund.getFundStatus() + "\"");
    queryParams.add(fund.getId());
    String sql = "UPDATE "+ fullBudgetTableName +" SET jsonb = jsonb_set(jsonb,'{budgetStatus}', ?::jsonb) " +
      "WHERE((fundId=?) " +
      "AND (budget.fiscalYearId IN " +
      "(SELECT id FROM " + fullFYTableName + " WHERE  current_date between (jsonb->>'periodStart')::timestamp " +
      "AND (jsonb->>'periodEnd')::timestamp)));";

    fundTx.getPgClient().execute(fundTx.getConnection(), sql, queryParams, event -> {
      if (event.failed()) {
        HelperUtils.handleFailure(promise, event);
      } else {
        promise.complete(fundTx);
      }
    });
    return promise.future();
  }
}
