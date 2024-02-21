package org.folio.service.ledger;

import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

import java.util.List;

public interface LedgerService {

  Future<Ledger> getLedgerByTransaction(Transaction transaction, DBConn conn);
  Future<List<Ledger>> getLedgersByIds(List<String> ids, DBConn conn);
}
