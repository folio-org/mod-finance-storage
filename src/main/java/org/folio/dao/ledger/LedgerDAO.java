package org.folio.dao.ledger;

import org.folio.rest.jaxrs.model.Ledger;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

import java.util.List;

public interface LedgerDAO {

  Future<Ledger> getLedgerById(String id, DBConn conn);
  Future<List<Ledger>> getLedgersByIds(List<String> ids, DBConn conn);

}
