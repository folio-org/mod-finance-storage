package org.folio.rest.service.summary;

import org.folio.rest.dao.summary.OrderTransactionSummaryDAO;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;

public class EncumbranceTransactionSummaryService extends AbstractTransactionSummaryService<OrderTransactionSummary> {

  public EncumbranceTransactionSummaryService() {
    super(new OrderTransactionSummaryDAO());
  }

  @Override
  protected String getSummaryId(Transaction transaction) {
    return transaction.getEncumbrance().getSourcePurchaseOrderId();
  }

  @Override
  protected boolean isProcessed(OrderTransactionSummary summary) {
    return summary.getNumTransactions() < 0;
  }

  @Override
  protected void setTransactionsSummariesProcessed(OrderTransactionSummary summary) {
    summary.setNumTransactions(-Math.abs(summary.getNumTransactions()));
  }

  @Override
  public Integer getNumTransactions(OrderTransactionSummary summary) {
    return summary.getNumTransactions();
  }
}
