package org.folio.service.rollover;

import static org.folio.dao.rollover.LedgerFiscalYearRolloverDAO.LEDGER_FISCAL_YEAR_ROLLOVER_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;

public class UniqueValidationService {

  private static final Logger log = LogManager.getLogger(UniqueValidationService.class);
  private final LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;

  public UniqueValidationService(LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO) {
    this.ledgerFiscalYearRolloverDAO = ledgerFiscalYearRolloverDAO;
  }

  public Future<Void> validationOfUniqueness(LedgerFiscalYearRollover rollover, DBClient client) {
    Promise<Void> promise = Promise.promise();

    if (rollover.getRolloverType().equals(LedgerFiscalYearRollover.RolloverType.COMMIT)) {
      String query = buildValidationOfUniquenessQuery(rollover, client.getTenantId());
      ledgerFiscalYearRolloverDAO.validationOfUniqueness(query, client)
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

  private String buildValidationOfUniquenessQuery(LedgerFiscalYearRollover rollover, String tenantId) {
    return String.format(
      "SELECT NOT EXISTS(SELECT ledgerId, fromFiscalYearId FROM %s WHERE ledgerId = '%s' AND fromFiscalYearId = '%s');",
      getFullTableName(tenantId, LEDGER_FISCAL_YEAR_ROLLOVER_TABLE), rollover.getLedgerId(), rollover.getFromFiscalYearId());
  }
}
