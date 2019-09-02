package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class GroupAPI implements FinanceStorageGroups {

  private static final String GROUPS_TABLE = "group";

  @Override
  @Validate
  public void getFinanceStorageGroups(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(GROUPS_TABLE, Group.class, GroupCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageGroupsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageGroups(String lang, Group entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(GROUPS_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageGroupsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageGroupsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(GROUPS_TABLE, Group.class, id, okapiHeaders, vertxContext, GetFinanceStorageGroupsByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageGroupsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(GROUPS_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageGroupsByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageGroupsById(String id, String lang, Group entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(GROUPS_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageGroupsByIdResponse.class, asyncResultHandler);
  }
}
