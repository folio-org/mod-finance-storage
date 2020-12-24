package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.persist.PgUtil;
import org.folio.service.budget.BudgetService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class BudgetAPI implements FinanceStorageBudgets {
  public static final String BUDGET_TABLE = "budget";

  @Autowired
  private BudgetService budgetService;

  public BudgetAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageBudgets(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(BUDGET_TABLE, Budget.class, BudgetCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageBudgetsResponse.class, responseAsyncResult -> {
        Response result = responseAsyncResult.result();
        if (!responseAsyncResult.failed() && responseAsyncResult.result().getEntity() instanceof BudgetCollection) {
          budgetService.updateBudgetsWithCalculatedFilds(((BudgetCollection) result.getEntity()).getBudgets());
        }
        asyncResultHandler.handle(responseAsyncResult);
      });
  }

  @Override
  @Validate
  public void postFinanceStorageBudgets(String lang, Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(BUDGET_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageBudgetsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageBudgetsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BUDGET_TABLE, Budget.class, id, okapiHeaders, vertxContext, GetFinanceStorageBudgetsByIdResponse.class, responseAsyncResult -> {
      Response result = responseAsyncResult.result();
      if (!responseAsyncResult.failed() && responseAsyncResult.result().getEntity() instanceof Budget) {
        Budget budget = (Budget) result.getEntity();
        budgetService.updateBudgetsWithCalculatedFilds(List.of(budget));
      }
      asyncResultHandler.handle(responseAsyncResult);
    });
  }

  @Override
  @Validate
  public void deleteFinanceStorageBudgetsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    budgetService.deleteById(id, vertxContext, okapiHeaders, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageBudgetsById(String id, String lang, Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BUDGET_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageBudgetsByIdResponse.class, asyncResultHandler);
  }
}
