package org.folio.dao.ledger;

import javax.ws.rs.core.Response;

import org.folio.rest.exception.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Ledger;

import io.vertx.core.Future;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

import java.util.Collections;
import java.util.List;

public class LedgerPostgresDAO implements LedgerDAO {

  private static final Logger logger = LogManager.getLogger();

  public static final String LEDGER_NOT_FOUND = "Ledger not found by id=%s";
  public static final String LEDGER_TABLE = "ledger";

  @Override
  public Future<Ledger> getLedgerById(String id, DBConn conn) {
    logger.debug("Trying to get ledger by id {}", id);
    return conn.getById(LEDGER_TABLE, id, Ledger.class)
      .map(ledger -> {
        if (ledger == null) {
          logger.warn(String.format(LEDGER_NOT_FOUND, id));
          throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), String.format(LEDGER_NOT_FOUND, id));
        }
        return ledger;
      })
      .onSuccess(ledger -> logger.info("Successfully retrieved a ledger by id {}", id))
      .onFailure(e -> logger.error("Getting ledger by id {} failed", id, e));
  }

  @Override
  public Future<List<Ledger>> getLedgersByIds(List<String> ids, DBConn conn) {
    logger.debug("Trying to get ledgers by ids = {}", ids);
    if (ids.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    ids.forEach(id -> criterionBuilder.with("id", id));
    return getLedgersByCriterion(criterionBuilder.build(), conn);
  }

  private Future<List<Ledger>> getLedgersByCriterion(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get ledgers by criterion = {}", criterion);
    return conn.get(LEDGER_TABLE, Ledger.class, criterion, false)
      .map(Results::getResults)
      .onSuccess(ledgers -> logger.info("Successfully retrieved {} ledgers by criterion = {}", ledgers.size(), criterion))
      .onFailure(e -> logger.error("Getting ledgers by criterion = {} failed", criterion, e));
  }
}
