package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.BatchIdCollection;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.service.budget.BudgetService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.jaxrs.resource.FinanceStorageBudgets.PostFinanceStorageBudgetsBatchResponse.respond200WithApplicationJson;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildResponseWithLocation;

public class BudgetAPI implements FinanceStorageBudgets {

  private static final Logger logger = LogManager.getLogger(BudgetAPI.class);

  public static final String BUDGET_TABLE = "budget";

  @Autowired
  private BudgetService budgetService;

  public BudgetAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageBudgets(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(BUDGET_TABLE, Budget.class, BudgetCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageBudgetsResponse.class, responseAsyncResult -> {
        Response result = responseAsyncResult.result();
        if (!responseAsyncResult.failed() && responseAsyncResult.result().getEntity() instanceof BudgetCollection) {
          budgetService.updateBudgetsWithCalculatedFields(((BudgetCollection) result.getEntity()).getBudgets());
        }
        asyncResultHandler.handle(responseAsyncResult);
      });
  }

  @Override
  @Validate
  public void postFinanceStorageBudgets(Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    budgetService.createBudget(entity, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(createdBudget -> asyncResultHandler.handle(succeededFuture(
        buildResponseWithLocation(okapiHeaders.get(OKAPI_URL), "/finance-storage/budgets", createdBudget))))
      .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t)));
  }

  @Override
  public void postFinanceStorageBudgetsBatch(BatchIdCollection entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new RequestContext(vertxContext, okapiHeaders).toDBClient()
      .withConn(conn -> budgetService.getBudgetsByIds(entity.getIds(), conn)
        .map(budgets -> new BudgetCollection().withBudgets(budgets).withTotalRecords(budgets.size()))
        .onSuccess(budgets -> asyncResultHandler.handle(succeededFuture(respond200WithApplicationJson(budgets))))
        .onFailure(throwable -> {
          HttpException cause = (HttpException) throwable;
          logger.error("Failed to get funds by ids {}", entity.getIds(), cause);
          HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
        }));
  }

  @Override
  @Validate
  public void getFinanceStorageBudgetsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BUDGET_TABLE, Budget.class, id, okapiHeaders, vertxContext, GetFinanceStorageBudgetsByIdResponse.class, responseAsyncResult -> {
      Response result = responseAsyncResult.result();
      if (!responseAsyncResult.failed() && responseAsyncResult.result().getEntity() instanceof Budget) {
        Budget budget = (Budget) result.getEntity();
        budgetService.updateBudgetsWithCalculatedFields(List.of(budget));
      }
      asyncResultHandler.handle(responseAsyncResult);
    });
  }

  @Override
  @Validate
  public void deleteFinanceStorageBudgetsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    budgetService.deleteById(id, vertxContext, okapiHeaders, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageBudgetsById(String id, Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BUDGET_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageBudgetsByIdResponse.class, asyncResultHandler);
  }
}
