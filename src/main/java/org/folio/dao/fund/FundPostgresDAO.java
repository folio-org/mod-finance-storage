package org.folio.dao.fund;

import static org.folio.rest.impl.FundAPI.FUND_TABLE;

import java.util.Collections;
import java.util.List;

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

  private Future<List<Fund>> getFundsByCriterion(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get funds by criterion = {}", criterion);
    return conn.get(FUND_TABLE, Fund.class, criterion, false)
      .map(Results::getResults)
      .onSuccess(funds -> logger.info("Successfully retrieved {} funds by criterion = {}", funds.size(), criterion))
      .onFailure(e -> logger.error("Getting funds by criterion = {} failed", criterion, e));
  }

}
