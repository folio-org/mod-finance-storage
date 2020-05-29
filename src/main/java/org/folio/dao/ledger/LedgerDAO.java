package org.folio.dao.ledger;

import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface LedgerDAO {

  Future<Ledger> getLedgerById(String id, DBClient client);

}
