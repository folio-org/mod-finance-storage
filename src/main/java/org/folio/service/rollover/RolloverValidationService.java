package org.folio.service.rollover;

import static org.folio.dao.rollover.LedgerFiscalYearRolloverDAO.LEDGER_FISCAL_YEAR_ROLLOVER_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ArrayTuple;

public class RolloverValidationService {

  private static final Logger log = LogManager.getLogger(RolloverValidationService.class);

  public Future<Void> checkRolloverExists(LedgerFiscalYearRollover rollover, DBClient client) {
    Promise<Void> promise = Promise.promise();

    if (rollover.getRolloverType().equals(LedgerFiscalYearRollover.RolloverType.COMMIT)) {
      String query = buildValidateUniquenessQuery(client.getTenantId());
      Tuple parameters = getParametersForValidateUniquenessQuery(rollover);
      isRolloverExists(query, parameters, client)
        .onSuccess(isExists -> {
          if (Boolean.TRUE.equals(isExists)) {
            log.error("Not unique pair ledgerId {} and fromFiscalYearId {}", rollover.getLedgerId(), rollover.getFromFiscalYearId());
            promise.fail(new HttpException(Response.Status.CONFLICT.getStatusCode(), "Not unique pair ledgerId and fromFiscalYearId"));
          } else {
            promise.complete();
          }
        })
        .onFailure(promise::fail);
    } else {
      promise.complete();
    }
    return promise.future();
  }

  private Tuple getParametersForValidateUniquenessQuery(LedgerFiscalYearRollover rollover) {
    ArrayTuple params = new ArrayTuple(2);
    params.addValue(rollover.getLedgerId());
    params.addValue(rollover.getFromFiscalYearId());
    return params;
  }

  private String buildValidateUniquenessQuery(String tenantId) {
    return String.format("SELECT EXISTS(SELECT ledgerId, fromFiscalYearId FROM %s WHERE ledgerId = $1 AND fromFiscalYearId = $2);",
      getFullTableName(tenantId, LEDGER_FISCAL_YEAR_ROLLOVER_TABLE));
  }

  private Future<Boolean> isRolloverExists(String query, Tuple parameters, DBClient client) {
    Promise<Boolean> promise = Promise.promise();

    client.getPgClient()
      .execute(query, parameters, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          reply.result()
            .spliterator()
            .forEachRemaining(row -> promise.complete(row.get(Boolean.class, 0)));
        }
      });
    return promise.future();
  }
}
