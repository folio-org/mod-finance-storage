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

public class RolloverValidationService {

  private static final Logger log = LogManager.getLogger(RolloverValidationService.class);

  public Future<Void> checkRolloverExists(LedgerFiscalYearRollover rollover, DBClient client) {
    Promise<Void> promise = Promise.promise();

    if (rollover.getRolloverType().equals(LedgerFiscalYearRollover.RolloverType.COMMIT)) {
      String query = buildValidateUniquenessQuery(rollover, client.getTenantId());
      isRolloverNotExists(query, client)
        .onSuccess(isUnique -> {
          if (Boolean.TRUE.equals(isUnique)) {
            promise.complete();
          } else {
            log.error("Not unique pair ledgerId {} and fromFiscalYearId {}", rollover.getLedgerId(), rollover.getFromFiscalYearId());
            promise.fail(new HttpException(Response.Status.CONFLICT.getStatusCode(), "Not unique pair ledgerId and fromFiscalYearId"));
          }
        })
        .onFailure(promise::fail);
    } else {
      promise.complete();
    }
    return promise.future();
  }

  private String buildValidateUniquenessQuery(LedgerFiscalYearRollover rollover, String tenantId) {
    return String.format(
      "SELECT NOT EXISTS(SELECT ledgerId, fromFiscalYearId FROM %s WHERE ledgerId = '%s' AND fromFiscalYearId = '%s');",
      getFullTableName(tenantId, LEDGER_FISCAL_YEAR_ROLLOVER_TABLE), rollover.getLedgerId(), rollover.getFromFiscalYearId());
  }

  private Future<Boolean> isRolloverNotExists(String sql, DBClient client) {
    Promise<Boolean> promise = Promise.promise();

    client.getPgClient()
      .execute(sql, reply -> {
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
