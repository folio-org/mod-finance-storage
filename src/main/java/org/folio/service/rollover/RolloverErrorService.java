package org.folio.service.rollover;

import io.vertx.core.Future;
import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;

import java.util.List;
import java.util.UUID;

public class RolloverErrorService {
  private static final String LEDGER_ROLLOVER_ID = "ledgerRolloverId";
  private final RolloverErrorDAO rolloverErrorDAO;

  public RolloverErrorService(RolloverErrorDAO rolloverErrorDAO) {
    this.rolloverErrorDAO = rolloverErrorDAO;
  }

  public Future<List<LedgerFiscalYearRolloverError>> getRolloverErrors(Criterion filter, DBClient client) {
    return rolloverErrorDAO.get(filter, client);
  }

  public Future<Void> createRolloverError(LedgerFiscalYearRolloverError rolloverError, DBClient client) {
    if (rolloverError.getId() == null)
      rolloverError.setId(UUID.randomUUID().toString());
    return rolloverErrorDAO.create(rolloverError, client);
  }

  public Future<Void> deleteRolloverErrors(String ledgerRolloverId, DBClient client) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, ledgerRolloverId).build();
    return rolloverErrorDAO.deleteByQuery(criterion, client);
  }
}
