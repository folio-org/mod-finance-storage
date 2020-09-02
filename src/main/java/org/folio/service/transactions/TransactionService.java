package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;

public interface TransactionService {

  Future<Transaction> createTransaction(Transaction transaction, RequestContext requestContext);

  Future<Void> updateTransaction(Transaction transaction, RequestContext requestContext);

}
