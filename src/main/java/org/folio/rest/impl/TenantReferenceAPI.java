package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.tools.utils.TenantLoading;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

public class TenantReferenceAPI extends TenantAPI {
  private static final Logger log = LoggerFactory.getLogger(TenantReferenceAPI.class);

  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";

  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
                         Handler<AsyncResult<Response>> handler, Context cntxt) {
    log.info("postTenant");
    Vertx vertx = cntxt.owner();
    super.postTenant(tenantAttributes, headers, res -> {
      if (res.failed() || (res.succeeded() && (res.result().getStatus() < 200 || res.result().getStatus() > 299))) {
        handler.handle(res);
        return;
      }

      if (isLoadSample(tenantAttributes)) {
        TenantLoading tl = new TenantLoading();
        tl.withKey(PARAMETER_LOAD_SAMPLE)
          .withLead("data")
          .add("groups", getUriPath(FinanceStorageGroups.class))
          .add("fiscal-years", getUriPath(FinanceStorageFiscalYears.class))
          .add("ledgers", getUriPath(FinanceStorageLedgers.class))
          .add("funds", getUriPath(FinanceStorageFunds.class))
          .add("budgets", getUriPath(FinanceStorageBudgets.class))
          .add("transactions", getUriPath(FinanceStorageTransactions.class))
          .perform(tenantAttributes, headers, vertx, res1 -> {
            if (res1.failed()) {
              handler.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
                .respond500WithTextPlain(res1.cause().getLocalizedMessage())));
              return;
            }
            handler.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond201WithApplicationJson("")));
          });
      } else {
        handler.handle(res);
      }
    }, cntxt);

  }

  private boolean isLoadSample(TenantAttributes tenantAttributes) {
    // if a system parameter is passed from command line, ex: loadSample=true
    // that value is considered,Priority of Parameters:
    // Tenant Attributes > command line parameter > default(false)
    boolean loadSample = Boolean.parseBoolean(MODULE_SPECIFIC_ARGS.getOrDefault(PARAMETER_LOAD_SAMPLE,
      "false"));
    List<Parameter> parameters = tenantAttributes.getParameters();
    for (Parameter parameter : parameters) {
      if (PARAMETER_LOAD_SAMPLE.equals(parameter.getKey())) {
        loadSample = Boolean.parseBoolean(parameter.getValue());
      }
    }
    return loadSample;

  }

  @Override
  public void getTenant(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("getTenant");
    super.getTenant(headers, hndlr, cntxt);
  }

  @Override
  public void deleteTenant(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenant(headers, hndlr, cntxt);
  }

  private static String getUriPath(Class<?> clazz) {
    return clazz.getAnnotation(Path.class).value().replaceFirst("/", "");
  }
}
