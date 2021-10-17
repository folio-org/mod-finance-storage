package org.folio.dao.transactions;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

public class TemporaryInvoiceTransactionDAO extends BaseTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  public static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";

  public static final String INSERT_TEMPORARY_TRANSACTIONS = "INSERT INTO %s (id, jsonb) VALUES ($1, $2) " +
    "ON CONFLICT (concat_space_sql(lower(f_unaccent(jsonb->>'amount')), lower(f_unaccent(jsonb->>'fromFundId')), lower(f_unaccent(jsonb->>'sourceInvoiceId'))," +
    " lower(f_unaccent(jsonb->>'sourceInvoiceLineId')), lower(f_unaccent(jsonb->>'toFundId')), lower(f_unaccent(jsonb->>'transactionType')), lower(f_unaccent(jsonb->>'expenseClassId'))))" +
    " DO UPDATE SET id = excluded.id RETURNING id;";


  public TemporaryInvoiceTransactionDAO() {
    super(TEMPORARY_INVOICE_TRANSACTIONS);
  }


  protected String createTempTransactionQuery(String tenantId) {
    return String.format(INSERT_TEMPORARY_TRANSACTIONS, getFullTableName(tenantId, getTableName()));
  }

}
