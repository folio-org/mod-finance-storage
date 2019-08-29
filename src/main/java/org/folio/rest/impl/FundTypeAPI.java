package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FundType;
import org.folio.rest.jaxrs.model.FundTypeCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFundTypes;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class FundTypeAPI implements FinanceStorageFundTypes {

  private static final String FUND_TYPE_TABLE = "fund_type";

  @Override
  public void getFinanceStorageFundTypes(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FUND_TYPE_TABLE, FundType.class, FundTypeCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageFundTypesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFundTypes(String lang, FundType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_TYPE_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundTypesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundTypesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_TYPE_TABLE, FundType.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundTypesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundTypesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_TYPE_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundTypesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundTypesById(String id, String lang, FundType entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_TYPE_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFundTypesByIdResponse.class, asyncResultHandler);
  }
}
