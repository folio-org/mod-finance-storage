package org.folio.rest.dao.summary;

import org.folio.rest.jaxrs.model.OrderTransactionSummary;

public class OrderTransactionSummaryDAO extends BaseTransactionSummaryDAO<OrderTransactionSummary> implements TransactionSummaryDao<OrderTransactionSummary> {

  public static final String ORDER_TRANSACTION_SUMMARIES = "order_transaction_summaries";

  @Override
  protected String getTableName() {
    return ORDER_TRANSACTION_SUMMARIES;
  }

  @Override
  protected Class<OrderTransactionSummary> getClazz() {
    return OrderTransactionSummary.class;
  }
}
