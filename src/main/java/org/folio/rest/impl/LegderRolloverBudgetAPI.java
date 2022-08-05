package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerRolloversBudgets;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudgetCollection;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class LegderRolloverBudgetAPI implements FinanceStorageLedgerRolloversBudgets {

  public static final String LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE = "ledger_fiscal_year_rollover_budget";

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversBudgets(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE, LedgerFiscalYearRolloverBudget.class, LedgerFiscalYearRolloverBudgetCollection.class, query,
      offset, limit, okapiHeaders, vertxContext, FinanceStorageLedgerRolloversBudgets.GetFinanceStorageLedgerRolloversBudgetsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgerRolloversBudgets(String lang, LedgerFiscalYearRolloverBudget entity, Map<String, String> okapiHeaders,
              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE, entity, okapiHeaders, vertxContext, FinanceStorageLedgerRolloversBudgets.PostFinanceStorageLedgerRolloversBudgetsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversBudgetsById(String id, String lang, Map<String, String> okapiHeaders,
              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE, LedgerFiscalYearRolloverBudget.class, id, okapiHeaders, vertxContext, FinanceStorageLedgerRolloversBudgets.GetFinanceStorageLedgerRolloversBudgetsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgerRolloversBudgetsById(String id, String lang, Map<String, String> okapiHeaders,
              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE, id, okapiHeaders, vertxContext, FinanceStorageLedgerRolloversBudgets.DeleteFinanceStorageLedgerRolloversBudgetsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageLedgerRolloversBudgetsById(String id, String lang, LedgerFiscalYearRolloverBudget entity,
              Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE, entity, id, okapiHeaders, vertxContext, FinanceStorageLedgerRolloversBudgets.PutFinanceStorageLedgerRolloversBudgetsByIdResponse.class, asyncResultHandler);
  }
}
