package org.folio.rest.service.summary;

import org.folio.rest.dao.summary.InvoiceTransactionSummaryDAO;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;

public class PendingPaymentsTransactionSummaryService extends AbstractTransactionSummaryService<InvoiceTransactionSummary> {

  public PendingPaymentsTransactionSummaryService() {
    super(new InvoiceTransactionSummaryDAO());
  }

  @Override
  protected String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  @Override
  protected boolean isProcessed(InvoiceTransactionSummary summary) {
    return summary.getNumEncumbrances() < 0;
  }

  @Override
  protected void setTransactionsSummariesProcessed(InvoiceTransactionSummary summary) {
    summary.setNumEncumbrances(-summary.getNumEncumbrances());
  }

  @Override
  public Integer getNumTransactions(InvoiceTransactionSummary summary) {
    return summary.getNumEncumbrances();
  }
}
