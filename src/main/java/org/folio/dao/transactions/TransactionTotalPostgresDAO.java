package org.folio.dao.transactions;

import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getRowSetAsList;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.TransactionTotal;
import org.folio.rest.jaxrs.model.TransactionTotalBatch;
import org.folio.rest.jaxrs.model.TransactionTotalCollection;
import org.folio.rest.jaxrs.model.TransactionType;
import org.folio.rest.persist.DBConn;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;

public class TransactionTotalPostgresDAO implements TransactionTotalDAO {

  public static final String TRANSACTION_TOTALS_VIEW = "transaction_totals_view";
  private static final String TRANSACTION_TOTALS_BATCH_QUERY =
    "SELECT jsonb FROM %s WHERE (jsonb->>'fiscalYearId') = $1 AND jsonb->>'transactionType' = ANY ($2) AND (jsonb->>'%s') = ANY ($3)";

  @Override
  public Future<TransactionTotalCollection> getTransactionTotalsBatch(DBConn conn, TransactionTotalBatch batchRequest, Map<String, String> okapiHeaders) {
    var fundIdsColumnPair = getFundIdsColumnPair(batchRequest.getToFundIds(), batchRequest.getFromFundIds());
    var transactionTypes = batchRequest.getTransactionTypes().stream().map(TransactionType::value).toArray(String[]::new);

    var tableName = getFullTableName(TenantTool.tenantId(okapiHeaders), TRANSACTION_TOTALS_VIEW);
    var sql = TRANSACTION_TOTALS_BATCH_QUERY.formatted(tableName, fundIdsColumnPair.getLeft());
    var params = Tuple.of(batchRequest.getFiscalYearId(), transactionTypes, fundIdsColumnPair.getRight());
    return conn.execute(sql, params)
      .map(rows -> getRowSetAsList(rows, TransactionTotal.class))
      .map(transactionTotals -> new TransactionTotalCollection()
        .withTransactionTotals(transactionTotals)
        .withTotalRecords(transactionTotals.size()));
  }

  private Pair<String, String[]> getFundIdsColumnPair(List<String> toFundIds, List<String> fromFundIds) {
    return CollectionUtils.isNotEmpty(toFundIds)
      ? Pair.of("toFundId", toFundIds.toArray(String[]::new))
      : Pair.of("fromFundId", fromFundIds.toArray(String[]::new));
  }

}
