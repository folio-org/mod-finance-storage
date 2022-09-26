package org.folio.service.rollover;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.jaxrs.model.RolloverStatus;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;

public class RolloverProgressService {

  public static final String LEDGER_ROLLOVER_ID = "ledgerRolloverId";
  private final RolloverProgressDAO rolloverProgressDAO;
  private final RolloverErrorService rolloverErrorService;

  public RolloverProgressService(RolloverProgressDAO rolloverProgressDAO, RolloverErrorService rolloverErrorService) {
    this.rolloverProgressDAO = rolloverProgressDAO;
    this.rolloverErrorService = rolloverErrorService;
  }

  public Future<Void> createRolloverProgress(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    return rolloverProgressDAO.create(progress, client);
  }

  public Future<Void> updateRolloverProgress(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    return rolloverProgressDAO.update(progress, client);
  }

  public Future<LedgerFiscalYearRolloverProgress> getLedgerRolloverProgressForRollover(String rolloverId, DBClient client){
    Criterion criterion = new CriterionBuilder()
      .with(LEDGER_ROLLOVER_ID, rolloverId).build();
    return rolloverProgressDAO.get(criterion, client)
      .map(progresses -> {
        if (progresses.isEmpty()) {
          throw new HttpException(404, "Can't retrieve rollover progress by rolloverId");
        } else {
          return progresses.get(0);
        }
      });
  }

  public Future<Void> calculateAndUpdateFinancialProgressStatus(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, progress.getLedgerRolloverId())
      .build();
    return rolloverErrorService.getRolloverErrors(criterion, client)
      .compose(rolloverErrors -> {
        if (rolloverErrors.isEmpty()) {
          progress.setFinancialRolloverStatus(RolloverStatus.SUCCESS);
        } else {
          progress.setFinancialRolloverStatus(RolloverStatus.ERROR);
        }
        return rolloverProgressDAO.update(progress, client);
      });
  }

  public Future<Void> calculateAndUpdateOverallProgressStatus(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, progress.getLedgerRolloverId())
      .build();
    return rolloverErrorService.getRolloverErrors(criterion, client)
      .compose(rolloverErrors -> {
        if (rolloverErrors.isEmpty()) {
          progress.setOverallRolloverStatus(RolloverStatus.SUCCESS);
        } else {
          progress.setOverallRolloverStatus(RolloverStatus.ERROR);
        }
        return rolloverProgressDAO.update(progress, client);
      });
  }

  public Future<Void> deleteRolloverProgress(String rolloverId, DBClient client) {
    return rolloverProgressDAO.delete(rolloverId, client);
  }
}
