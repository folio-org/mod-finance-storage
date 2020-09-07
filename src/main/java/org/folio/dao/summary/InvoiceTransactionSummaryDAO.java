package org.folio.dao.summary;

public class InvoiceTransactionSummaryDAO extends BaseTransactionSummaryDAO implements TransactionSummaryDao {

  public static final String INVOICE_TRANSACTION_SUMMARIES = "invoice_transaction_summaries";

  @Override
  protected String getTableName() {
    return INVOICE_TRANSACTION_SUMMARIES;
  }
}
