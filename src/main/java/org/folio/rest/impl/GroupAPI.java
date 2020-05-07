package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupCollection;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.GroupFundFiscalYearCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupFundFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.service.GroupService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class GroupAPI implements FinanceStorageGroups, FinanceStorageGroupFundFiscalYears {

  private static final String GROUPS_TABLE = "groups";
  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";

  private GroupService groupService;

  public GroupAPI(Vertx vertx, String tenantId) {
    groupService = new GroupService(vertx, tenantId);
  }

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
    groupService.createGroup( entity, vertxContext, asyncResultHandler);
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

  @Override
  @Validate
  public void getFinanceStorageGroupFundFiscalYears(String query, int offset, int limit, String lang,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(GROUP_FUND_FY_TABLE, GroupFundFiscalYear.class, GroupFundFiscalYearCollection.class, query, offset, limit,
        okapiHeaders, vertxContext, GetFinanceStorageGroupFundFiscalYearsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageGroupFundFiscalYears(String lang, GroupFundFiscalYear entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(GROUP_FUND_FY_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageGroupFundFiscalYearsResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageGroupFundFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(GROUP_FUND_FY_TABLE, GroupFundFiscalYear.class, id, okapiHeaders, vertxContext,
        GetFinanceStorageGroupFundFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageGroupFundFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(GROUP_FUND_FY_TABLE, id, okapiHeaders, vertxContext,
        DeleteFinanceStorageGroupFundFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageGroupFundFiscalYearsById(String id, String lang, GroupFundFiscalYear entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(GROUP_FUND_FY_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageGroupFundFiscalYearsByIdResponse.class,
        asyncResultHandler);
  }
}
