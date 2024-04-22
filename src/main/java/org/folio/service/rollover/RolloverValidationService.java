package org.folio.service.rollover;

import static org.folio.dao.rollover.LedgerFiscalYearRolloverDAO.LEDGER_FISCAL_YEAR_ROLLOVER_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.folio.rest.exception.HttpException;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ArrayTuple;
import org.folio.rest.persist.DBConn;

public class RolloverValidationService {

  private static final Logger logger = LogManager.getLogger(RolloverValidationService.class);

  public Future<Void> checkRolloverExists(LedgerFiscalYearRollover rollover, DBConn conn) {
    Promise<Void> promise = Promise.promise();

    if (rollover.getRolloverType().equals(LedgerFiscalYearRollover.RolloverType.COMMIT)) {
      String query = buildValidateUniquenessQuery(conn.getTenantId());
      Tuple parameters = getParametersForValidateUniquenessQuery(rollover);
      isRolloverExists(query, parameters, conn)
        .onSuccess(isExists -> {
          if (Boolean.TRUE.equals(isExists)) {
            logger.error("checkRolloverExists:: Not unique pair ledgerId {} and fromFiscalYearId {}", rollover.getLedgerId(), rollover.getFromFiscalYearId());
            promise.fail(new HttpException(Response.Status.CONFLICT.getStatusCode(), "Not unique pair ledgerId and fromFiscalYearId"));
          } else {
            logger.info("checkRolloverExists:: A pair ledgerId {} and fromFiscalYearId {} are unique", rollover.getLedgerId(), rollover.getFromFiscalYearId());
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
    return String.format("SELECT EXISTS(SELECT ledgerId, fromFiscalYearId FROM %s WHERE ledgerId = $1 AND fromFiscalYearId = $2 AND jsonb->>'rolloverType' = 'Commit');",
      getFullTableName(tenantId, LEDGER_FISCAL_YEAR_ROLLOVER_TABLE));
  }

  private Future<Boolean> isRolloverExists(String query, Tuple parameters, DBConn conn) {
    logger.debug("isRolloverExists:: Is rollover exists by query {}", query);

    // Note: the SELECT EXISTS query always returns exactly one result row
    return conn.execute(query, parameters)
      .map(rowSet -> rowSet.iterator().next().get(Boolean.class, 0))
      .onFailure(e -> logger.debug("isRolloverExists:: Getting rollover by query {}", query, e));
  }
}
