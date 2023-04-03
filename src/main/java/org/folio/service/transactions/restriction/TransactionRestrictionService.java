package org.folio.service.transactions.restriction;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

public interface TransactionRestrictionService {

  void handleValidationError(Transaction transaction);
  Future<Void> verifyBudgetHasEnoughMoney(Transaction transaction, DBClient dbClient);
}
