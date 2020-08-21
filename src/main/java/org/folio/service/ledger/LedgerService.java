package org.folio.service.ledger;

import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface LedgerService {

  Future<Ledger> getLedgerByTransaction(Transaction transaction, DBClient dbClient);
}
