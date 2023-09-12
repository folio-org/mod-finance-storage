package org.folio.dao.fund;

import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class FundPostgresDAO implements FundDAO {

  private static final Logger logger = LogManager.getLogger(FundPostgresDAO.class);

  public Future<List<Fund>> getFunds(Criterion criterion, DBClient client) {
    logger.debug("Trying to get funds by criterion = {}", criterion);
    Promise<List<Fund>> promise = Promise.promise();
    client.getPgClient().get(FUND_TABLE, Fund.class, criterion, false, reply -> {
      if (reply.failed()) {
        logger.error("Getting funds by criterion = {} failed", criterion, reply.cause());
        handleFailure(promise, reply);
      } else {
        List<Fund> funds = reply.result()
          .getResults();
        logger.info("Successfully retrieved {} funds by criterion = {}", funds.size(), criterion);
        promise.complete(funds);
      }
    });
    return promise.future();
  }
}
