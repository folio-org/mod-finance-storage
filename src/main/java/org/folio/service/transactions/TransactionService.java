package org.folio.service.transactions;

import java.util.Map;

import org.folio.rest.jaxrs.model.Transaction;

import io.vertx.core.Context;
import io.vertx.core.Future;

public interface TransactionService {

  Future<Transaction> createTransaction(Transaction transaction, Context context, Map<String, String> headers);

  Future<Void> updateTransaction(String id, Transaction transaction, Context context, Map<String, String> headers);

}
