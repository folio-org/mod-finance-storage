package org.folio.dao.fiscalyear;

import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.Objects;
import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBClient;

public class FiscalYearPostgresDAO implements FiscalYearDAO {

  private static final Logger logger = LogManager.getLogger(FiscalYearPostgresDAO.class);

  private static final String FISCAL_YEAR_NOT_FOUND = "Fiscal year not found by id=%s";

  public Future<FiscalYear> getFiscalYearById(String id, DBClient client) {
    logger.debug("Trying to get fiscal year by id {}", id);
    Promise<FiscalYear> promise = Promise.promise();
    client.getPgClient().getById(FISCAL_YEAR_TABLE, id, FiscalYear.class,
      reply -> {
        if (reply.failed()) {
          logger.error("Getting fiscal year by id {} failed", id, reply.cause());
          handleFailure(promise, reply);
        } else if (Objects.isNull(reply.result())) {
          logger.warn(String.format(FISCAL_YEAR_NOT_FOUND, id));
          promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), String.format(FISCAL_YEAR_NOT_FOUND, id)));
        } else {
          logger.info("Successfully retrieved a fiscal year by id {}", id);
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }
}
