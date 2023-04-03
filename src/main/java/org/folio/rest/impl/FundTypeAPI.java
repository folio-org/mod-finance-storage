package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FundType;
import org.folio.rest.jaxrs.model.FundTypeCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFundTypes;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class FundTypeAPI implements FinanceStorageFundTypes {

  private static final String FUND_TYPE_TABLE = "fund_type";

  @Override
  @Validate
  public void getFinanceStorageFundTypes(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FUND_TYPE_TABLE, FundType.class, FundTypeCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageFundTypesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFundTypes(FundType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_TYPE_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundTypesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundTypesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_TYPE_TABLE, FundType.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundTypesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundTypesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_TYPE_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundTypesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundTypesById(String id, FundType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_TYPE_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFundTypesByIdResponse.class, asyncResultHandler);
  }
}
