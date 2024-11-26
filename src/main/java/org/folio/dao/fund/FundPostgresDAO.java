package org.folio.dao.fund;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import io.vertx.core.Promise;
import io.vertx.sqlclient.Tuple;
import java.util.Collections;
import java.util.List;

import java.util.UUID;
import org.folio.rest.exception.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.interfaces.Results;

import javax.ws.rs.core.Response;

public class FundPostgresDAO implements FundDAO {
  private static final Logger logger = LogManager.getLogger();
  private static final String FUND_NOT_FOUND = "Fund not found, id=%s";

  @Override
  public Future<Fund> getFundById(String id, DBConn conn) {
    logger.debug("Trying to get fund by id {}", id);
    return conn.getById(FUND_TABLE, id, Fund.class)
      .map(fund -> {
        if (fund == null) {
          String message = String.format(FUND_NOT_FOUND, id);
          logger.warn(message);
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(), message);
        }
        return fund;
      })
      .onSuccess(fund -> logger.info("Successfully retrieved a fund by id {}", id))
      .onFailure(e -> logger.error("Getting fund by id {} failed", id, e));
  }

  @Override
  public Future<List<Fund>> getFundsByIds(List<String> ids, DBConn conn) {
    logger.debug("Trying to get funds by ids = {}", ids);
    if (ids.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    ids.forEach(id -> criterionBuilder.with("id", id));
    return getFundsByCriterion(criterionBuilder.build(), conn);
  }

  @Override
  public Future<Void> updateFundById(String fundId, Fund fund, DBConn conn) {
    logger.debug("Trying to update finance storage fund by id {}", id);
    fund.setId(id);
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    return vertxContext.runOnContext(event ->
      isFundStatusChanged(fund, client)
        .onComplete(result -> {
          if (result.failed()) {
            HttpException cause = (HttpException) result.cause();
            logger.error("Failed to update the finance storage fund with Id {}", fund.getId(), cause);
            HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
          } else if (result.result() == null) {
            logger.warn("Finance storage fund with id {} not found", id);
            asyncResultHandler.handle(succeededFuture(
              FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.respond404WithTextPlain("Not found")));
          } else if (Boolean.TRUE.equals(result.result())) {
            handleFundStatusUpdate(fund, client).onComplete(asyncResultHandler);
          } else {
            PgUtil.put(FUND_TABLE, fund, id, okapiHeaders, vertxContext,
              FinanceStorageFunds.PutFinanceStorageFundsByIdResponse.class, asyncResultHandler);
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
      .onSuccess(x -> logger.info("Fund record {} was successfully updated", fund))
      .mapEmpty();
  }

  private Future<Void> updateRelatedCurrentFYBudgets(Fund fund, DBConn conn) {
    String fullBudgetTableName = getFullTableName(conn.getTenantId(), BUDGET_TABLE);
    String fullFYTableName = getFullTableName(conn.getTenantId(), FISCAL_YEAR_TABLE);

    String sql = "UPDATE " + fullBudgetTableName + " SET jsonb = jsonb_set(jsonb,'{budgetStatus}', $1) " +
      "WHERE((fundId=$2) " +
      "AND (budget.fiscalYearId IN " +
      "(SELECT id FROM " + fullFYTableName + " WHERE  current_date between (jsonb->>'periodStart')::timestamp " +
      "AND (jsonb->>'periodEnd')::timestamp)));";

    return conn.execute(sql, Tuple.of(fund.getFundStatus().value(), UUID.fromString(fund.getId())))
      .mapEmpty();
  }

  private Future<List<Fund>> getFundsByCriterion(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get funds by criterion = {}", criterion);
    return conn.get(FUND_TABLE, Fund.class, criterion, false)
      .map(Results::getResults)
      .onSuccess(funds -> logger.info("Successfully retrieved {} funds by criterion = {}", funds.size(), criterion))
      .onFailure(e -> logger.error("Getting funds by criterion = {} failed", criterion, e));
  }

}
