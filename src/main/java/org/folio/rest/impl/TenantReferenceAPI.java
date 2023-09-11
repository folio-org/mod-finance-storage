package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgetExpenseClasses;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.jaxrs.resource.FinanceStorageExpenseClasses;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageFundTypes;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupFundFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class TenantReferenceAPI extends TenantAPI {

  private static final Logger logger = LogManager.getLogger(TenantReferenceAPI.class);

  private static final String PARAMETER_LOAD_REFERENCE = "loadReference";
  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";
  public static final String LOAD_SYNC_PARAMETER = "loadSync";

  @Override
  public Future<Integer> loadData(TenantAttributes attributes, String tenantId, Map<String, String> headers, Context vertxContext) {
    logger.debug("Trying to load tenant data with tenantId {}", tenantId);
    Vertx vertx = vertxContext.owner();
    Promise<Integer> promise = Promise.promise();

    TenantLoading tl = new TenantLoading();
    buildDataLoadingParameters(attributes, tl);

    tl.perform(attributes, headers, vertx, res -> {
      if (res.failed()) {
        logger.error("Failed to load tenant data", res.cause());
        promise.fail(res.cause());
      } else {
        logger.info("Tenant data loaded successfully");
        promise.complete(res.result());
      }
    });
    return promise.future();
  }

  private boolean buildDataLoadingParameters(TenantAttributes tenantAttributes, TenantLoading tl) {
    boolean loadData = false;
    if (isLoadReference(tenantAttributes)) {
      tl.withKey(PARAMETER_LOAD_REFERENCE)
        .withLead("data")
        .add("expense-classes", getUriPath(FinanceStorageExpenseClasses.class))
        .add("fund-types", getUriPath(FinanceStorageFundTypes.class));
      loadData = true;
    }
    if (isLoadSample(tenantAttributes)) {
      tl.withKey(PARAMETER_LOAD_SAMPLE)
        .withLead("data")
        .add("groups", getUriPath(FinanceStorageGroups.class))
        .add("fiscal-years", getUriPath(FinanceStorageFiscalYears.class))
        .add("ledgers", getUriPath(FinanceStorageLedgers.class))
        .add("funds", getUriPath(FinanceStorageFunds.class))
        .add("budgets", getUriPath(FinanceStorageBudgets.class))
        .add("budget-expense-classes", getUriPath(FinanceStorageBudgetExpenseClasses.class))
        .add("group-fund-fiscal-years", getUriPath(FinanceStorageGroupFundFiscalYears.class))
        .add("transactions/allocations", getUriPath(FinanceStorageTransactions.class))
        .withPostIgnore() // Payments and credits don't support PUT
        .add("transactions/transfers", getUriPath(FinanceStorageTransactions.class));
      loadData = true;
    }
    return loadData;
  }

  private boolean isLoadReference(TenantAttributes tenantAttributes) {
    // if a system parameter is passed from command line, ex: loadReference=true
    // that value is considered,Priority of Parameters:
    // Tenant Attributes > command line parameter > default(false)
    boolean loadReference = Boolean.parseBoolean(MODULE_SPECIFIC_ARGS.getOrDefault(PARAMETER_LOAD_REFERENCE, "false"));
    List<Parameter> parameters = tenantAttributes.getParameters();
    for (Parameter parameter : parameters) {
      if (PARAMETER_LOAD_REFERENCE.equals(parameter.getKey())) {
        loadReference = Boolean.parseBoolean(parameter.getValue());
      }
    }
    return loadReference;

  }

  private boolean isLoadSample(TenantAttributes tenantAttributes) {
    // if a system parameter is passed from command line, ex: loadSample=true
    // that value is considered,Priority of Parameters:
    // Tenant Attributes > command line parameter > default(false)
    boolean loadSample = Boolean.parseBoolean(MODULE_SPECIFIC_ARGS.getOrDefault(PARAMETER_LOAD_SAMPLE, "false"));
    List<Parameter> parameters = tenantAttributes.getParameters();
    for (Parameter parameter : parameters) {
      if (PARAMETER_LOAD_SAMPLE.equals(parameter.getKey())) {
        loadSample = Boolean.parseBoolean(parameter.getValue());
      }
    }
    return loadSample;

  }

  @Override
  public void deleteTenantByOperationId(String operationId, Map<String, String> headers, Handler<AsyncResult<Response>> handler,
      Context ctx) {
    logger.info("Trying to delete tenant by operation id {}", operationId);
    super.deleteTenantByOperationId(operationId, headers, res -> {
      Vertx vertx = ctx.owner();
      String tenantId = TenantTool.tenantId(headers);
      PostgresClient.getInstance(vertx, tenantId)
        .closeClient(event -> handler.handle(res));
    }, ctx);
  }

  private static String getUriPath(Class<?> clazz) {
    return HelperUtils.getEndpoint(clazz)
      .replaceFirst("/", "");
  }
}
