package org.folio.dao.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.folio.rest.util.ResponseUtils;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

public class TemporaryEncumbrancePostgresDAO implements TemporaryEncumbranceDAO {

  private static final Logger logger = LogManager.getLogger(TemporaryEncumbrancePostgresDAO.class);

  public static final String TEMPORARY_ENCUMBRANCE_TRANSACTIONS_TABLE = "tmp_encumbered_transactions";
  private static final String TEMPORARY_ENCUMBRANCE_TRANSACTIONS_QUERY = "SELECT jsonb FROM tmp_encumbered_transactions WHERE %s ";

  @Override
  public Future<List<Transaction>> getTempTransactions(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get temp transactions by query: {}", criterion);
    return conn.execute(format(TEMPORARY_ENCUMBRANCE_TRANSACTIONS_QUERY, criterion.getWhere()))
      .map(result -> {
        List<Transaction> transactions = new ArrayList<>();
        result.spliterator()
          .forEachRemaining(row -> transactions.add(row.get(JsonObject.class, 0).mapTo(Transaction.class)));
        logger.info("Successfully retrieved {} temp transactions by query: {}", transactions.size(), criterion);
        return transactions;
      })
      .recover(ResponseUtils::handleFailure);
  }

}
