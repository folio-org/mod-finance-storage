package org.folio.dao.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class TemporaryEncumbranceTransactionDAO extends BaseTemporaryTransactionsDAO{

  public static final String TEMPORARY_ENCUMBRANCE_TRANSACTIONS_TABLE = "tmp_encumbered_transactions";
  private static final String TEMPORARY_ENCUMBRANCE_TRANSACTIONS_QUERY = "SELECT jsonb FROM tmp_encumbered_transactions WHERE %s ";

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
    return conn.execute(format(TEMPORARY_ENCUMBRANCE_TRANSACTIONS_QUERY, criterion.getWhere()))
      .transform(reply -> {
        if (reply.failed()) {
          return Future.future(promise -> handleFailure(promise, reply));
        } else {
          List<Transaction> transactions = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> transactions.add(row.get(JsonObject.class, 0).mapTo(Transaction.class)));
          return Future.succeededFuture(transactions);
        }
      });
  }

}
