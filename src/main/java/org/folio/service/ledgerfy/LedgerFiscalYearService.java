package org.folio.service.ledgerfy;

import java.util.List;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface LedgerFiscalYearService {
  Future<List<LedgerFY>> getLedgerFiscalYearsByBudgets(List<Budget> budgets, DBClient client);

  Future<Void> updateLedgerFiscalYears(List<LedgerFY> ledgersFYears, DBClient client);
}
