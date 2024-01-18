package org.folio.service.rollover;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.jaxrs.model.RolloverStatus;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;

public class RolloverProgressService {

  public static final String LEDGER_ROLLOVER_ID = "ledgerRolloverId";
  private final RolloverProgressDAO rolloverProgressDAO;
  private final RolloverErrorService rolloverErrorService;

  public RolloverProgressService(RolloverProgressDAO rolloverProgressDAO, RolloverErrorService rolloverErrorService) {
    this.rolloverProgressDAO = rolloverProgressDAO;
    this.rolloverErrorService = rolloverErrorService;
  }

  public Future<Void> createRolloverProgress(LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    return rolloverProgressDAO.create(progress, conn);
  }

  public Future<Void> updateRolloverProgress(LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    return rolloverProgressDAO.update(progress, conn);
  }

  public Future<LedgerFiscalYearRolloverProgress> getLedgerRolloverProgressForRollover(String rolloverId, DBConn conn){
    Criterion criterion = new CriterionBuilder()
      .with(LEDGER_ROLLOVER_ID, rolloverId).build();
    return rolloverProgressDAO.get(criterion, conn)
      .map(progresses -> {
        if (progresses.isEmpty()) {
          throw new HttpException(404, "Can't retrieve rollover progress by rolloverId");
        } else {
          return progresses.get(0);
        }
      });
  }

  public Future<Void> calculateAndUpdateFinancialProgressStatus(LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, progress.getLedgerRolloverId())
      .build();
    return rolloverErrorService.getRolloverErrors(criterion, conn)
      .compose(rolloverErrors -> {
        if (rolloverErrors.isEmpty()) {
          progress.setFinancialRolloverStatus(RolloverStatus.SUCCESS);
        } else {
          progress.setFinancialRolloverStatus(RolloverStatus.ERROR);
        }
        return rolloverProgressDAO.update(progress, conn);
      });
  }

  public Future<Void> calculateAndUpdateOverallProgressStatus(LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, progress.getLedgerRolloverId())
      .build();
    return rolloverErrorService.getRolloverErrors(criterion, conn)
      .compose(rolloverErrors -> {
        if (rolloverErrors.isEmpty()) {
          progress.setOverallRolloverStatus(RolloverStatus.SUCCESS);
        } else {
          progress.setOverallRolloverStatus(RolloverStatus.ERROR);
        }
        return rolloverProgressDAO.update(progress, conn);
      });
  }

  public Future<Void> deleteRolloverProgress(String rolloverId, DBConn conn) {
    return rolloverProgressDAO.delete(rolloverId, conn);
  }
}
