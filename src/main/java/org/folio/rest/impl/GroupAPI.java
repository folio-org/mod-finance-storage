package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupCollection;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.GroupFundFiscalYearCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupFundFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
import org.folio.rest.persist.PgUtil;
import org.folio.service.group.GroupService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.jaxrs.resource.FinanceStorageGroupFundFiscalYears.PutFinanceStorageGroupFundFiscalYearsByIdResponse.respond204;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildResponseWithLocation;

public class GroupAPI implements FinanceStorageGroups, FinanceStorageGroupFundFiscalYears {

  public static final String GROUPS_TABLE = "groups";
  public static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";

  @Autowired
  private GroupService groupService;

  public GroupAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageGroups(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(GROUPS_TABLE, Group.class, GroupCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageGroupsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageGroups(Group entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    groupService.createGroup(entity, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(createdBudget -> asyncResultHandler.handle(succeededFuture(
        buildResponseWithLocation(okapiHeaders.get(OKAPI_URL), "/finance-storage/groups", createdBudget))))
      .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t)));
  }

  @Override
  @Validate
  public void getFinanceStorageGroupsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(GROUPS_TABLE, Group.class, id, okapiHeaders, vertxContext, GetFinanceStorageGroupsByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageGroupsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(GROUPS_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageGroupsByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageGroupsById(String id, Group entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    groupService.updateGroup( entity, id, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(group -> asyncResultHandler.handle(succeededFuture(respond204())))
      .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t)));
  }

  @Override
  @Validate
  public void getFinanceStorageGroupFundFiscalYears(String query, String totalRecords, int offset, int limit,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(GROUP_FUND_FY_TABLE, GroupFundFiscalYear.class, GroupFundFiscalYearCollection.class, query, offset, limit,
        okapiHeaders, vertxContext, GetFinanceStorageGroupFundFiscalYearsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageGroupFundFiscalYears(GroupFundFiscalYear entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(GROUP_FUND_FY_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageGroupFundFiscalYearsResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageGroupFundFiscalYearsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(GROUP_FUND_FY_TABLE, GroupFundFiscalYear.class, id, okapiHeaders, vertxContext,
        GetFinanceStorageGroupFundFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageGroupFundFiscalYearsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(GROUP_FUND_FY_TABLE, id, okapiHeaders, vertxContext,
        DeleteFinanceStorageGroupFundFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageGroupFundFiscalYearsById(String id, GroupFundFiscalYear entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(GROUP_FUND_FY_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageGroupFundFiscalYearsByIdResponse.class,
        asyncResultHandler);
  }
}
