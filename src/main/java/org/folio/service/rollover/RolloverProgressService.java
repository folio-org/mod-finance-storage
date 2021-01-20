package org.folio.service.rollover;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.ArrayList;
import java.util.List;
import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;

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

  public Future<LedgerFiscalYearRolloverProgress> getLedgerRolloverProgressForRollover(String rolloverId, DBClient client){
    Criterion criterion = new CriterionBuilder()
      .with("ledgerRolloverId", rolloverId).build();
    return rolloverProgressDAO.get(criterion, client)
      .map(progresses -> {
        if (progresses.isEmpty()) {
          throw new HttpStatusException(404, "Can't retrieve rollover progress by rolloverId");
        } else {
          return progresses.get(0);
        }
      });
  }

  public Future<List<LedgerFiscalYearRolloverError>> getLedgerRolloverErrorsForRollover(String rolloverId, DBClient client){
    Criterion criterion = new CriterionBuilder()
      .with("ledgerRolloverId", rolloverId).build();
    return rolloverErrorDAO.get(criterion, client);
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

  public Future<Void> deleteRolloverProgress(String rolloverId, DBClient client) {
    return rolloverProgressDAO.delete(rolloverId, client);
  }

  public Future<Void> deleteRolloverErrors(String ledgerRolloverId, DBClient client) {
    return getLedgerRolloverErrorsForRollover(ledgerRolloverId, client)
      .compose(errors -> {
        List<Future> futures = new ArrayList<>();
        errors.forEach(error -> futures.add(rolloverErrorDAO.delete(error.getId(), client)));
        return CompositeFuture.all(futures).mapEmpty();
      });
  }
}
