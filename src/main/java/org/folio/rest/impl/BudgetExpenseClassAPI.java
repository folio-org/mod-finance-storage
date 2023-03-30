package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.jaxrs.model.BudgetExpenseClassCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgetExpenseClasses;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class BudgetExpenseClassAPI implements FinanceStorageBudgetExpenseClasses {
  public static final String BUDGET_EXPENSE_CLASS_TABLE = "budget_expense_class";
  @Override
  public void getFinanceStorageBudgetExpenseClasses(String query,
                                                    String totalRecords,
                                                    int offset,
                                                    int limit,
                                                    Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler,
                                                    Context vertxContext) {

    PgUtil.get(BUDGET_EXPENSE_CLASS_TABLE, BudgetExpenseClass.class, BudgetExpenseClassCollection.class, query, offset,
      limit, okapiHeaders, vertxContext, GetFinanceStorageBudgetExpenseClassesResponse.class, asyncResultHandler);
  }

  @Override
  public void postFinanceStorageBudgetExpenseClasses(BudgetExpenseClass entity,
                                                     Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                                     Context vertxContext) {

    PgUtil.post(BUDGET_EXPENSE_CLASS_TABLE, entity, okapiHeaders, vertxContext,
      PostFinanceStorageBudgetExpenseClassesResponse.class, asyncResultHandler);
  }

  @Override
  public void getFinanceStorageBudgetExpenseClassesById(String id,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    PgUtil.getById(BUDGET_EXPENSE_CLASS_TABLE, BudgetExpenseClass.class, id, okapiHeaders, vertxContext,
      GetFinanceStorageBudgetExpenseClassesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteFinanceStorageBudgetExpenseClassesById(String id,
                                                           Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {

    PgUtil.deleteById(BUDGET_EXPENSE_CLASS_TABLE, id, okapiHeaders, vertxContext,
      DeleteFinanceStorageBudgetExpenseClassesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putFinanceStorageBudgetExpenseClassesById(String id,
                                                        BudgetExpenseClass entity,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    PgUtil.put(BUDGET_EXPENSE_CLASS_TABLE, entity, id, okapiHeaders, vertxContext,
      PutFinanceStorageBudgetExpenseClassesByIdResponse.class, asyncResultHandler);
  }
}
