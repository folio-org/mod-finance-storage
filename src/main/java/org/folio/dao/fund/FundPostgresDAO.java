package org.folio.dao.fund;

import static org.folio.rest.impl.FundAPI.FUND_TABLE;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

public class FundPostgresDAO implements FundDAO {

  private static final Logger logger = LogManager.getLogger(FundPostgresDAO.class);

  public Future<List<Fund>> getFunds(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get funds by criterion = {}", criterion);
    return conn.get(FUND_TABLE, Fund.class, criterion, false)
      .map(Results::getResults)
      .onSuccess(funds -> logger.info("Successfully retrieved {} funds by criterion = {}", funds.size(), criterion))
      .onFailure(e -> logger.error("Getting funds by criterion = {} failed", criterion, e));
  }
}
