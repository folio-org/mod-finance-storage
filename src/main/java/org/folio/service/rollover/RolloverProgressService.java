package org.folio.service.rollover;

import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;

public class RolloverProgressService {

  private final RolloverProgressDAO rolloverProgressDAO;
  private final RolloverErrorDAO rolloverErrorDAO;

  public RolloverProgressService(RolloverProgressDAO rolloverProgressDAO, RolloverErrorDAO rolloverErrorDAO) {
    this.rolloverProgressDAO = rolloverProgressDAO;
    this.rolloverErrorDAO = rolloverErrorDAO;
  }

  public Future<Void> createRolloverProgress(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    return rolloverProgressDAO.create(progress, client);
  }

  public Future<Void> updateRolloverProgress(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    return rolloverProgressDAO.update(progress, client);
  }

  public Future<Void> calculateAndUpdateFinancialProgressStatus(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    Criterion criterion = new CriterionBuilder().with("ledgerRolloverId", progress.getLedgerRolloverId())
      .build();
    return rolloverErrorDAO.get(criterion, client)
      .compose(rolloverErrors -> {
        if (rolloverErrors.isEmpty()) {
          progress.setFinancialRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.SUCCESS);
        } else {
          progress.setFinancialRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.ERROR);
        }
        return rolloverProgressDAO.update(progress, client);
      });
  }

  public Future<Void> calculateAndUpdateOverallProgressStatus(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    Criterion criterion = new CriterionBuilder().with("ledgerRolloverId", progress.getLedgerRolloverId())
      .build();
    return rolloverErrorDAO.get(criterion, client)
      .compose(rolloverErrors -> {
        if (rolloverErrors.isEmpty()) {
          progress.setOverallRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.SUCCESS);
        } else {
          progress.setOverallRolloverStatus(LedgerFiscalYearRolloverProgress.OverallRolloverStatus.ERROR);
        }
        return rolloverProgressDAO.update(progress, client);
      });
  }
}
