package org.folio.rest.dao.transactions;

import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface TemporaryTransactionDAO {

  Future<Transaction> createTempTransaction(Transaction transaction, String summaryId, DBClient client);
  Future<List<Transaction>> getTempTransactionsBySummaryId(String summaryId, DBClient client);
  Future<Integer> deleteTempTransactions(String summaryId, DBClient client);

}
