package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildNoContentResponse;
import static org.folio.rest.util.ResponseUtils.buildOkResponse;
import static org.folio.rest.util.ResponseUtils.buildResponseWithLocation;

import javax.ws.rs.core.Response;
import java.util.Map;

import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.jaxrs.resource.FinanceStorageExchangeRateSource;
import org.folio.service.exchangerate.ExchangeRateSourceService;
import org.folio.spring.SpringContextUtil;
import org.folio.tools.store.SecureStore;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class ExchangeRateSourceAPI implements FinanceStorageExchangeRateSource {

  @Autowired
  private SecureStore secureStore;
  @Autowired
  private ExchangeRateSourceService exchangeRateSourceService;

  public ExchangeRateSourceAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getFinanceStorageExchangeRateSource(Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    exchangeRateSourceService.getExchangeRateSource(new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(exchangeRateSource -> asyncResultHandler.handle(buildOkResponse(exchangeRateSource)))
      .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t)));
  }

  @Override
  public void postFinanceStorageExchangeRateSource(ExchangeRateSource entity, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    exchangeRateSourceService.saveExchangeRateSource(entity, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(exchangeRateSource -> asyncResultHandler.handle(succeededFuture(
        buildResponseWithLocation(okapiHeaders.get(OKAPI_URL), "/finance-storage/exchange-rate-source", exchangeRateSource))))
      .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t)));
  }

  @Override
  public void putFinanceStorageExchangeRateSourceById(String id, ExchangeRateSource entity, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    exchangeRateSourceService.updateExchangeRateSource(id, entity, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(v -> asyncResultHandler.handle(buildNoContentResponse()))
      .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t)));
  }

  @Override
  public void deleteFinanceStorageExchangeRateSourceById(String id, Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    exchangeRateSourceService.deleteExchangeRateSource(id, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(v -> asyncResultHandler.handle(buildNoContentResponse()))
      .onFailure(t -> asyncResultHandler.handle(buildErrorResponse(t)));
  }

}
