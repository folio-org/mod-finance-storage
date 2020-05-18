package org.folio.rest.service;

import java.util.Map;

import org.folio.rest.jaxrs.model.Transaction;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class AbstractTransactionService implements TransactionService {
  public static final String TRANSACTION_TABLE = "transaction";
  static final String TRANSACTION_LOCATION_PREFIX = "/finance-storage/transactions/";

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public Future<Void> updateTransaction(Transaction transaction, Context context, Map<String, String> headers) {
    throw new UnsupportedOperationException("Transactions are Immutable");
  }

}
