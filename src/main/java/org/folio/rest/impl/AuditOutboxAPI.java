package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.FinanceStorageAuditOutbox;
import org.folio.service.audit.AuditOutboxService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class AuditOutboxAPI implements FinanceStorageAuditOutbox {

  @Autowired
  private AuditOutboxService auditOutboxService;

  public AuditOutboxAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void postFinanceStorageAuditOutboxProcess(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    auditOutboxService.processOutboxEventLogs(okapiHeaders, vertxContext)
      .onSuccess(count -> asyncResultHandler.handle(Future.succeededFuture(Response.ok().build())))
      .onFailure(cause -> asyncResultHandler.handle(Future.failedFuture(cause)));
  }
}
