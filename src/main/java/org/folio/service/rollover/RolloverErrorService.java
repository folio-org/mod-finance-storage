package org.folio.service.rollover;

import io.vertx.core.Future;
import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;

import java.util.List;
import java.util.UUID;

public class RolloverErrorService {
  private static final String LEDGER_ROLLOVER_ID = "ledgerRolloverId";
  private final RolloverErrorDAO rolloverErrorDAO;

  public RolloverErrorService(RolloverErrorDAO rolloverErrorDAO) {
    this.rolloverErrorDAO = rolloverErrorDAO;
  }

  public Future<List<LedgerFiscalYearRolloverError>> getRolloverErrors(Criterion filter, DBConn conn) {
    return rolloverErrorDAO.get(filter, conn);
  }

  public Future<Void> createRolloverError(LedgerFiscalYearRolloverError rolloverError, DBConn conn) {
    if (rolloverError.getId() == null)
      rolloverError.setId(UUID.randomUUID().toString());
    return rolloverErrorDAO.create(rolloverError, conn);
  }

  public Future<Void> deleteRolloverErrors(String ledgerRolloverId, DBConn conn) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, ledgerRolloverId).build();
    return rolloverErrorDAO.deleteByQuery(criterion, conn);
  }
}
