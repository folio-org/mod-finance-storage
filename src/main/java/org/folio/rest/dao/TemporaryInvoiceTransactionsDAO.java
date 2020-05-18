package org.folio.rest.dao;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TemporaryInvoiceTransactionsDAO extends AbstractTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";

  public static final String INSERT_TEMPORARY_TRANSACTIONS = "INSERT INTO %s (id, jsonb) VALUES (?, ?::JSON) "
    + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
    + "lower(f_unaccent(jsonb ->> 'sourceInvoiceId'::text)), "
    + "lower(f_unaccent(jsonb ->> 'sourceInvoiceLineId'::text)), "
    + "lower(f_unaccent(jsonb ->> 'toFundId'::text)), "
    + "lower(f_unaccent(jsonb ->> 'transactionType'::text))) DO UPDATE SET id = excluded.id RETURNING id;";

  protected TemporaryInvoiceTransactionsDAO() {
    super(TEMPORARY_INVOICE_TRANSACTIONS);
  }


  protected String createTempTransactionQuery(String tenantId) {
    return String.format(INSERT_TEMPORARY_TRANSACTIONS, getFullTableName(tenantId, getTableName()));
  }

  @Override
  protected Criterion getSummaryIdCriteria(String summaryId) {
    return null;
  }

}
