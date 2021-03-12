package org.folio.rest.impl;

import static org.folio.dao.ledger.LedgerPostgresDAO.LEDGER_TABLE;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

import java.util.List;
import java.util.Map;

import java.util.function.Supplier;
import javax.ws.rs.core.Response;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.SemVer;
import org.folio.rest.jaxrs.model.Ledger;
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
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class TenantReferenceAPI extends TenantAPI {
  private static final Logger log = LogManager.getLogger(TenantReferenceAPI.class);

  private static final String PARAMETER_LOAD_REFERENCE = "loadReference";
  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";
  public static final String LOAD_SYNC_PARAMETER = "loadSync";

  @Override
  public Future<Integer> loadData(TenantAttributes attributes, String tenantId, Map<String, String> headers, Context vertxContext) {
    log.info("postTenant");
    Vertx vertx = vertxContext.owner();

    TenantLoading tl = new TenantLoading();
    buildDataLoadingParameters(attributes, tl);

    return Future.succeededFuture()
      // migrationModule value it the same as fromModuleVersion from schema.json
      .compose(v -> migration(attributes, "mod-finance-storage-6.1.0", () -> customizeYourMigrationLogicHere(headers, vertxContext)))
      .compose(v -> migration(attributes, "mod-finance-storage-5.0.0", () -> customizeYourMigrationLogicHere(headers, vertxContext)))
      .compose(v -> {
        Promise<Integer> promise = Promise.promise();
        tl.perform(attributes, headers, vertx, res -> {
          if (res.failed()) {
            promise.fail(res.cause());
          } else {
            promise.complete(res.result());
          }
        });
        return promise.future();
      });
  }

  // Implement your own migration logic here
  private Future<Void> customizeYourMigrationLogicHere(Map<String, String> headers, Context vertxContext) {
    Promise<Void> promise = Promise.promise();
    vertxContext.runOnContext(event -> {
      DBClient client = new DBClient(vertxContext, headers);
      client.startTx()
        .compose(v -> retrieveSomeDataFromDB(client))
        .compose(v -> client.endTx())
        .onSuccess(v -> {
          log.info("ok");
          promise.complete();
        })
        .onFailure(v -> {
          log.info("Some error");
          promise.fail("Some error");
        });
    });
    return promise.future();
  }

  private Future<Void> retrieveSomeDataFromDB(DBClient client) {
    Promise<Void> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder() .build();

    client.getPgClient().get(LEDGER_TABLE, Ledger.class, criterion, false, reply -> {
      if (reply.failed()) {
        promise.fail("error");
      } else {
        log.info("Ledger record {} was successfully retrieved", reply.result().toString());
        promise.complete();
      }
    });
    return promise.future();
  }

  private Future<Void> migration(TenantAttributes attributes, String migrationModule, Supplier<Future<Void>> supplier) {
    SemVer moduleTo = moduleVersionToSemVer(migrationModule);
    SemVer currentModuleVersion = moduleVersionToSemVer(attributes.getModuleFrom());
    if (moduleTo.compareTo(currentModuleVersion) > 0){
      return supplier.get();
    }
    return Future.succeededFuture();
  }

  private static SemVer moduleVersionToSemVer(String version) {
    try {
      return new SemVer(version);
    } catch (IllegalArgumentException ex) {
      return new ModuleId(version).getSemVer();
    }
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
        .withPostOnly() // Payments and credits don't support PUT
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
  public void deleteTenantByOperationId(String operationId, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr,
      Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenantByOperationId(operationId, headers, res -> {
      Vertx vertx = cntxt.owner();
      String tenantId = TenantTool.tenantId(headers);
      PostgresClient.getInstance(vertx, tenantId)
        .closeClient(event -> hndlr.handle(res));
    }, cntxt);
  }

  private static String getUriPath(Class<?> clazz) {
    return HelperUtils.getEndpoint(clazz)
      .replaceFirst("/", "");
  }
}
