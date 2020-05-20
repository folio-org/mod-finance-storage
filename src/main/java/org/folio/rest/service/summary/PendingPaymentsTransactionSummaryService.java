package org.folio.rest.service.summary;

import org.folio.rest.dao.summary.TransactionSummaryDao;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;

public class PendingPaymentsTransactionSummaryService extends AbstractTransactionSummaryService<InvoiceTransactionSummary> {

  public PendingPaymentsTransactionSummaryService(TransactionSummaryDao<InvoiceTransactionSummary> transactionSummaryDao) {
    super(transactionSummaryDao);
  }

  @Override
  protected String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  @Override
  protected boolean isProcessed(InvoiceTransactionSummary summary) {
    return summary.getNumPendingPayments() < 0;
  }

  @Override
  protected void setTransactionsSummariesProcessed(InvoiceTransactionSummary summary) {
    summary.setNumPendingPayments(-summary.getNumPendingPayments());
  }

  @Override
  public Integer getNumTransactions(InvoiceTransactionSummary summary) {
    return summary.getNumPendingPayments();
  }
}
