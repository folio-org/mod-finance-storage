package org.folio.rest.impl;

import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildOkResponse;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.resource.FinanceStorageJobNumber;
import org.folio.service.jobnumber.JobNumberService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class JobNumberAPI implements FinanceStorageJobNumber {

  private static final Logger log = LogManager.getLogger(JobNumberAPI.class);

  @Autowired
  private JobNumberService jobNumberService;

  public JobNumberAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getFinanceStorageJobNumber(String type, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("Trying to get job number");
    jobNumberService.getNextNumber(type, new RequestContext(vertxContext, okapiHeaders))
      .onComplete(event -> {
        if (event.succeeded()) {
          asyncResultHandler.handle(buildOkResponse(event.result()));
        } else {
          asyncResultHandler.handle(buildErrorResponse(event.cause()));
        }
      });
  }
}
