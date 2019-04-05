package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.FundDistributionCollection;
import org.folio.rest.jaxrs.resource.FundDistribution;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class FundDistributionAPI implements FundDistribution {
  private static final String FUND_DISTRIBUTION_TABLE = "fund_distribution";

  private String idFieldName = "id";

  public FundDistributionAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getFundDistribution(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.FundDistribution, FundDistributionCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.FundDistribution.class, FundDistributionCollection.class, GetFundDistributionResponse.class);
      QueryHolder cql = new QueryHolder(FUND_DISTRIBUTION_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  public void postFundDistribution(String lang, org.folio.rest.jaxrs.model.FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_DISTRIBUTION_TABLE, entity, okapiHeaders, vertxContext, PostFundDistributionResponse.class, asyncResultHandler);
  }

  @Override
  public void getFundDistributionById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_DISTRIBUTION_TABLE, org.folio.rest.jaxrs.model.FundDistribution.class, id, okapiHeaders, vertxContext, GetFundDistributionByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteFundDistributionById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_DISTRIBUTION_TABLE, id, okapiHeaders, vertxContext, DeleteFundDistributionByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putFundDistributionById(String id, String lang, org.folio.rest.jaxrs.model.FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_DISTRIBUTION_TABLE, entity, id, okapiHeaders, vertxContext, PutFundDistributionByIdResponse.class, asyncResultHandler);
  }
}
