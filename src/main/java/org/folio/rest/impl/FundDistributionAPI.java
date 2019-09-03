package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.FundDistributionCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFundDistributions;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class FundDistributionAPI implements FinanceStorageFundDistributions {
  private static final String FUND_DISTRIBUTION_TABLE = "fund_distribution";

  @Override
  @Validate
  public void getFinanceStorageFundDistributions(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FUND_DISTRIBUTION_TABLE, FundDistribution.class, FundDistributionCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageFundDistributionsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFundDistributions(String lang, FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_DISTRIBUTION_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundDistributionsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundDistributionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_DISTRIBUTION_TABLE, FundDistribution.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundDistributionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_DISTRIBUTION_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundDistributionsById(String id, String lang, FundDistribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_DISTRIBUTION_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFundDistributionsByIdResponse.class, asyncResultHandler);
  }
}
