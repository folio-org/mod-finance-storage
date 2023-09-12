package org.folio.dao.ledger;

import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.Objects;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class LedgerPostgresDAO implements LedgerDAO {

  private static final Logger logger = LogManager.getLogger(LedgerPostgresDAO.class);

  public static final String LEDGER_NOT_FOUND = "Ledger not found by id=%s";
  public static final String LEDGER_TABLE = "ledger";

  public Future<Ledger> getLedgerById(String id, DBClient client) {
    logger.debug("Trying to get ledger by id {}", id);
    Promise<Ledger> promise = Promise.promise();
    client.getPgClient().getById(LEDGER_TABLE, id, Ledger.class,
      reply-> {
        if (reply.failed()) {
          logger.error("Getting ledger by id {} failed", id, reply.cause());
          handleFailure(promise, reply);
        } else if (Objects.isNull(reply.result())) {
          logger.warn(String.format(LEDGER_NOT_FOUND, id));
          promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), String.format(LEDGER_NOT_FOUND, id)));
        } else {
          logger.info("Successfully retrieved a ledger by id {}", id);
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }

}
