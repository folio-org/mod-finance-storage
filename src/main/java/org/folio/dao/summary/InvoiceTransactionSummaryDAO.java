package org.folio.dao.summary;

import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;

public class InvoiceTransactionSummaryDAO extends BaseTransactionSummaryDAO<InvoiceTransactionSummary> implements TransactionSummaryDao<InvoiceTransactionSummary> {

  public static final String INVOICE_TRANSACTION_SUMMARIES = "invoice_transaction_summaries";

  @Override
  protected String getTableName() {
    return INVOICE_TRANSACTION_SUMMARIES;
  }

  @Override
  protected Class<InvoiceTransactionSummary> getClazz() {
    return InvoiceTransactionSummary.class;
  }
}
