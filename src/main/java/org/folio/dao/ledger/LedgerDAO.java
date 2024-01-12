package org.folio.dao.ledger;

import org.folio.rest.jaxrs.model.Ledger;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public interface LedgerDAO {

  Future<Ledger> getLedgerById(String id, DBConn conn);

}
