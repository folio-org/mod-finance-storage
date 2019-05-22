package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FundCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFund;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class FundAPI implements FinanceStorageFund {
  private static final String FUND_TABLE = "fund";

  private String idFieldName = "id";

  public FundAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getFinanceStorageFund(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.Fund, FundCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.Fund.class, FundCollection.class, GetFinanceStorageFundResponse.class);
      QueryHolder cql = new QueryHolder(FUND_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postFinanceStorageFund(String lang, org.folio.rest.jaxrs.model.Fund entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_TABLE, org.folio.rest.jaxrs.model.Fund.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundById(String id, String lang, org.folio.rest.jaxrs.model.Fund entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFundByIdResponse.class, asyncResultHandler);
  }
}
