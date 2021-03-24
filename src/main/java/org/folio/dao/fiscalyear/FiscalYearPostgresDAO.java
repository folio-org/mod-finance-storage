package org.folio.dao.fiscalyear;

import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.Objects;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBClient;

public class FiscalYearPostgresDAO implements FiscalYearDAO {

  public static final String FISCAL_YEAR_NOT_FOUND = "Fiscal year not found by id=%s";
  private final Logger logger = LogManager.getLogger(this.getClass());

  public Future<FiscalYear> getFiscalYearById(String id, DBClient client) {
    Promise<FiscalYear> promise = Promise.promise();
    client.getPgClient().getById(FISCAL_YEAR_TABLE, id, FiscalYear.class,
      reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else if (Objects.isNull(reply.result())) {
          logger.error(String.format(FISCAL_YEAR_NOT_FOUND, id));
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), String.format(FISCAL_YEAR_NOT_FOUND, id)));
        } else {
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }
}
