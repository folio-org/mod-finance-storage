package org.folio.dao.transactions;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.Criteria.Criterion;

public class TemporaryOrderTransactionDAO extends BaseTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String INSERT_TEMPORARY_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) VALUES ($1, $2) "
    + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePurchaseOrderId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePoLineId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'initialAmountEncumbered'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'status'::text))) DO UPDATE SET id = excluded.id RETURNING id;";

  public TemporaryOrderTransactionDAO() {
    super(TEMPORARY_ORDER_TRANSACTIONS);
  }

  @Override
  protected String createTempTransactionQuery(String tenantId) {
    return String.format(INSERT_TEMPORARY_ENCUMBRANCES, getFullTableName(tenantId, getTableName()));
  }

  @Override
  public Criterion getSummaryIdCriteria(String summaryId) {
    return new CriterionBuilder().with("encumbrance_sourcePurchaseOrderId", summaryId).build();
  }

}
