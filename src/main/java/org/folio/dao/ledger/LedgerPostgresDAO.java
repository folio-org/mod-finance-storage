package org.folio.dao.ledger;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Ledger;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public class LedgerPostgresDAO implements LedgerDAO {

  private static final Logger logger = LogManager.getLogger(LedgerPostgresDAO.class);

  public static final String LEDGER_NOT_FOUND = "Ledger not found by id=%s";
  public static final String LEDGER_TABLE = "ledger";

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

}
