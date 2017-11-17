package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.resource.FundResource;

import javax.ws.rs.core.Response;
import java.util.Map;

public class FundResourceAPI implements FundResource {
  @Override
  public void getFund(String query, String orderBy, Order order, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void postFund(String lang, Fund entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void getFundById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void deleteFundById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }

  @Override
  public void putFundById(String id, String lang, Fund entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

  }
}
