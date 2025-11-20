package org.folio.dao.transactions;

import java.util.Map;

import org.folio.rest.jaxrs.model.TransactionTotalBatch;
import org.folio.rest.jaxrs.model.TransactionTotalCollection;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Future;

public interface TransactionTotalDAO {

  Future<TransactionTotalCollection> getTransactionTotalsBatch(DBConn conn, TransactionTotalBatch batchRequest, Map<String, String> okapiHeaders);

}
