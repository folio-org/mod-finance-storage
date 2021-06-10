package org.folio.dao.ledger;

import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.Objects;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class LedgerPostgresDAO implements LedgerDAO {

  public static final String LEDGER_NOT_FOUND = "Ledger not found";
  public static final String LEDGER_TABLE = "ledger";

  public Future<Ledger> getLedgerById(String id, DBClient client) {
    Promise<Ledger> promise = Promise.promise();
    client.getPgClient().getById(LEDGER_TABLE, id, Ledger.class,
      reply-> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else if (Objects.isNull(reply.result())) {
          promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), LEDGER_NOT_FOUND));
        } else {
          promise.complete(reply.result());
        }
      });
    return promise.future();
  }

}
