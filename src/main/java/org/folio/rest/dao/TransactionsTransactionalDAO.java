package org.folio.rest.dao;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Tx;

import java.util.List;

public interface TransactionsTransactionalDAO  {

  Future<List<Transaction>> getTransactionsBySummaryId(String summaryId, Tx tx);

  Future<Integer> saveTransactionsToPermanentTable(String summaryId, Tx tx);

  Future<Integer> deleteTempTransactionsBySummaryId(String summaryId, Tx<List<Transaction>> tx);
}
