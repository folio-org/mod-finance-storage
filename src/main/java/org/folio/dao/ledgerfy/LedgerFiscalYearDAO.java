package org.folio.dao.ledgerfy;

import java.util.List;

import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;

public interface LedgerFiscalYearDAO {

  Future<List<LedgerFY>> getLedgerFiscalYears(Criterion criterion, DBClient client);

  Future<Void> saveLedgerFiscalYearRecords(List<LedgerFY> ledgerFYs, DBClient client);

  Future<Void> deleteLedgerFiscalYearRecords(Criterion criterion, DBClient client);

  Future<Void> updateLedgerFiscalYears(List<LedgerFY> ledgersFYears, DBClient client);
}
