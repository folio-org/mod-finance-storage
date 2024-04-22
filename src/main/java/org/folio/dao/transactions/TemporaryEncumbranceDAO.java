package org.folio.dao.transactions;

import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public interface TemporaryEncumbranceDAO {

  Future<List<Transaction>> getTempTransactions(Criterion criterion, DBConn conn);
}
