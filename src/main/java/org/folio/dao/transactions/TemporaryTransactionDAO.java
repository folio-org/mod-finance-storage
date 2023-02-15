package org.folio.dao.transactions;

import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface TemporaryTransactionDAO {

  Future<Transaction> createTempTransaction(Transaction transaction, String summaryId, String tenantId, Conn conn);
  Future<List<Transaction>> getTempTransactions(Criterion criterion, Conn conn);
  Future<List<Transaction>> getTempTransactionsBySummaryId(String summaryId, Conn conn);
  Future<Integer> deleteTempTransactions(String summaryId, DBClient client);
  Future<Integer> deleteTempTransactionsWithNewConn(String summaryId, DBClient client);
}
