package org.folio.dao.transactions;

import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public interface TemporaryTransactionDAO {

  Future<Transaction> createTempTransaction(Transaction transaction, String summaryId, String tenantId, DBConn conn);
  Future<List<Transaction>> getTempTransactions(Criterion criterion, DBConn conn);
  Future<List<Transaction>> getTempTransactionsBySummaryId(String summaryId, DBConn conn);
  Future<Integer> deleteTempTransactions(String summaryId, DBConn conn);
}
