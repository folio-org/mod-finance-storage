package org.folio.dao.fund;

import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import io.vertx.sqlclient.Tuple;
import java.util.Collections;
import java.util.List;

import java.util.UUID;
import org.folio.rest.exception.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
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
    logger.debug("Trying to update finance storage fund by id {}", fundId);
    fund.setId(fundId);
    return updateFund(fund, conn);
  }

  @Override
  public Future<Boolean> isFundStatusChanged(Fund fund, DBConn conn) {
    return getFundById(fund.getId(), conn)
      .map(existingFund -> existingFund.getFundStatus() != fund.getFundStatus());
  }

  @Override
  public Future<Void> updateRelatedCurrentFYBudgets(Fund fund, DBConn conn) {
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

  private Future<Void> updateFund(Fund fund, DBConn conn) {
    return conn.update(FUND_TABLE, fund, fund.getId())
      .onSuccess(x -> logger.info("Fund record {} was successfully updated", fund))
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
