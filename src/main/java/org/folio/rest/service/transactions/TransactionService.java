package org.folio.rest.service.transactions;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;

import java.util.Map;

public interface TransactionService {

  Future<Transaction> createTransaction(Transaction transaction, Context context, Map<String, String> headers);

  Future<Void> updateTransaction(String id, Transaction transaction, Context context, Map<String, String> headers);

}
