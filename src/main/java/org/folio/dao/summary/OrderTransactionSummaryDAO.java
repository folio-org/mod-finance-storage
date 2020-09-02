package org.folio.dao.summary;

public class OrderTransactionSummaryDAO extends BaseTransactionSummaryDAO implements TransactionSummaryDao {

  public static final String ORDER_TRANSACTION_SUMMARIES = "order_transaction_summaries";

  @Override
  protected String getTableName() {
    return ORDER_TRANSACTION_SUMMARIES;
  }

}
