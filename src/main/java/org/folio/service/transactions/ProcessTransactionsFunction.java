package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

import java.util.List;

@FunctionalInterface
public interface ProcessTransactionsFunction {

  Future<Void> apply(List<Transaction> transactions, String summaryId, DBClient client);

}
