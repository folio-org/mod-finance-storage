package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupBudgets;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.getEntitiesCollectionWithDistinctOn;

public class GroupBudgetAPI implements FinanceStorageGroupBudgets {

  public static final String GROUP_BUDGET_VIEW = "group_budgets_view";

  @Override
  @Validate
  public void getFinanceStorageGroupBudgets(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    EntitiesMetadataHolder<Budget, BudgetCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(
      Budget.class, BudgetCollection.class, GetFinanceStorageGroupBudgetsResponse.class);
    QueryHolder cql = new QueryHolder(GROUP_BUDGET_VIEW, query, offset, limit);
    getEntitiesCollectionWithDistinctOn(entitiesMetadataHolder, cql, ID_FIELD_NAME, asyncResultHandler, vertxContext, okapiHeaders);
  }
}
