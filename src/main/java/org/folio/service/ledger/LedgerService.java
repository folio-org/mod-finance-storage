package org.folio.service.ledger;

import java.util.Map;

import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;

import io.vertx.core.Context;
import io.vertx.core.Future;

public interface LedgerService {

  Future<Ledger> getLedgerByTransaction(Transaction transaction, Context context, Map<String, String> headers);
}
