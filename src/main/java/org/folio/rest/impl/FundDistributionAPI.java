package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Distribution;
import org.folio.rest.jaxrs.resource.FundDistributionResource;

import javax.ws.rs.core.Response;
import java.util.Map;

public class FundDistributionAPI implements FundDistributionResource {
  @Override
  public void getFundDistribution(String query, String orderBy, Order order, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void postFundDistribution(String lang, Distribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void getFundDistributionById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void deleteFundDistributionById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void putFundDistributionById(String id, String lang, Distribution entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }
}
