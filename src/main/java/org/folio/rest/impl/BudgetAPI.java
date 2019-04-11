package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.resource.Budget;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class BudgetAPI implements Budget {
  private static final String BUDGET_TABLE = "budget";

  private String idFieldName = "id";

  public BudgetAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getBudget(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.Budget, BudgetCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.Budget.class, BudgetCollection.class, GetBudgetResponse.class);
      QueryHolder cql = new QueryHolder(BUDGET_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postBudget(String lang, org.folio.rest.jaxrs.model.Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(BUDGET_TABLE, entity, okapiHeaders, vertxContext, PostBudgetResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getBudgetById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(BUDGET_TABLE, org.folio.rest.jaxrs.model.Budget.class, id, okapiHeaders, vertxContext, GetBudgetByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteBudgetById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(BUDGET_TABLE, id, okapiHeaders, vertxContext, DeleteBudgetByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putBudgetById(String id, String lang, org.folio.rest.jaxrs.model.Budget entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(BUDGET_TABLE, entity, id, okapiHeaders, vertxContext, PutBudgetByIdResponse.class, asyncResultHandler);
  }
}
