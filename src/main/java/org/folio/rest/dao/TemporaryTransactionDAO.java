package org.folio.rest.dao;

import java.util.List;

import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Tx;

import io.vertx.core.Future;

public interface TemporaryTransactionDAO {

  Future<Transaction> createTempTransaction(Transaction transaction, String summaryId, Vertx vertx, String tenantId);
  Future<List<Transaction>> getTempTransactionsBySummaryId(String summaryId, Vertx vertx, String tenantId);
}
