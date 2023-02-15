package org.folio.dao.transactions;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class TemporaryEncumbranceTransactionDAO extends BaseTemporaryTransactionsDAO{

  public static final String TEMPORARY_ENCUMBRANCE_TRANSACTIONS_TABLE = "tmp_encumbered_transactions";

  public TemporaryEncumbranceTransactionDAO() {
    super(TEMPORARY_ENCUMBRANCE_TRANSACTIONS_TABLE);
  }

  @Override
  protected String createTempTransactionQuery(String tenantId) {
    return EMPTY;
  }

  @Override
  protected Criterion getSummaryIdCriteria(String summaryId) {
    return new Criterion();
  }

  @Override
  public Future<List<Transaction>> getTempTransactions(Criterion criterion, Conn conn) {
    return conn.get(TEMPORARY_ENCUMBRANCE_TRANSACTIONS_TABLE, Transaction.class, criterion)
      .transform(reply -> reply.failed() ? Future.future(promise -> handleFailure(promise, reply))
        : Future.succeededFuture(reply.result().getResults()));
  }

}
