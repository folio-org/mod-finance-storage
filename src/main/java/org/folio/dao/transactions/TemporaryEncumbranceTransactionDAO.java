package org.folio.dao.transactions;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class TemporaryEncumbranceTransactionDAO extends BaseTemporaryTransactionsDAO{

  private static final String TEMPORARY_ENCUMBRANCE_TRANSACTIONS = "tmp_encumbered_transactions";
  private static final String TEMPORARY_ENCUMBRANCE_TRANSACTIONS_QUERY = "SELECT jsonb FROM tmp_encumbered_transactions WHERE %s ";

  public TemporaryEncumbranceTransactionDAO() {
    super(TEMPORARY_ENCUMBRANCE_TRANSACTIONS);
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
  public Future<List<Transaction>> getTempTransactions(Criterion criterion, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    client.getPgClient()
      .select(format(TEMPORARY_ENCUMBRANCE_TRANSACTIONS_QUERY, criterion.getWhere()), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Transaction> transactions = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> transactions.add(row.get(JsonObject.class, 0).mapTo(Transaction.class)));
          promise.complete(transactions);
        }
      });
    return promise.future();
  }

}
