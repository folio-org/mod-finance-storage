package org.folio.dao.transactions;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

public class TemporaryOrderTransactionDAO extends BaseTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String INSERT_TEMPORARY_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) VALUES ($1, $2) " +
    "ON CONFLICT (lower(f_unaccent(concat_space_sql(jsonb->>'amount', jsonb->>'fromFundId', " +
    "jsonb->'encumbrance'->>'sourcePurchaseOrderId', jsonb->'encumbrance'->>'sourcePoLineId' , " +
    "jsonb->'encumbrance'->>'initialAmountEncumbered', jsonb->'encumbrance'->>'status', jsonb->>'expenseClassId')))) " +
    "DO UPDATE SET id = excluded.id RETURNING id;";

  public TemporaryOrderTransactionDAO() {
    super(TEMPORARY_ORDER_TRANSACTIONS);
  }

  @Override
  protected String createTempTransactionQuery(String tenantId) {
    return String.format(INSERT_TEMPORARY_ENCUMBRANCES, getFullTableName(tenantId, getTableName()));
  }

}
