package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FundDistributionCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFundDistributions;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class FundDistributionAPI implements FinanceStorageFundDistributions {
  private static final String FUND_DISTRIBUTION_TABLE = "fund_distribution";

  private String idFieldName = "id";

  public FundDistributionAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getFinanceStorageFundDistributions(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.Distribution, FundDistributionCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.Distribution.class, FundDistributionCollection.class, GetFinanceStorageFundDistributionsResponse.class);
      QueryHolder cql = new QueryHolder(FUND_DISTRIBUTION_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postFinanceStorageFundDistributions(String lang, org.folio.rest.jaxrs.model.FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_DISTRIBUTION_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundDistributionsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundDistributionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_DISTRIBUTION_TABLE, org.folio.rest.jaxrs.model.FundDistribution.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundDistributionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_DISTRIBUTION_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundDistributionsById(String id, String lang, org.folio.rest.jaxrs.model.FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_DISTRIBUTION_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }
}
