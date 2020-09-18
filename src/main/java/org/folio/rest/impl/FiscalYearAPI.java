package org.folio.rest.impl;

import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.spring.SpringContextUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class FiscalYearAPI implements FinanceStorageFiscalYears {
  static final String FISCAL_YEAR_TABLE = "fiscal_year";

  private static final Logger log = LoggerFactory.getLogger(FiscalYearAPI.class);

  public FiscalYearAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYears(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FISCAL_YEAR_TABLE, FiscalYear.class, FiscalYearCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageFiscalYearsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFiscalYears(String lang, FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
     client.startTx()
        .compose(v -> saveFiscalYear(entity, client))
        .compose(v -> client.endTx())
        .onComplete(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("New fiscal year record creation has failed: {}", cause, entity);

            // The result of rollback operation is not so important, main failure cause is used to build the response
           client.rollbackTransaction().onComplete(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
          } else {
            log.info("New fiscal year record {} and associated data were successfully created", entity);
            asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageFiscalYearsResponse
              .respond201WithApplicationJson(entity, PostFinanceStorageFiscalYearsResponse.headersFor201()
                .withLocation(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class) + entity.getId()))));
          }
        })
    );
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FISCAL_YEAR_TABLE, FiscalYear.class, id, okapiHeaders, vertxContext, GetFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
     client.startTx()
        .compose(ok -> HelperUtils.deleteRecordById(id, client, FISCAL_YEAR_TABLE))
        .compose(v -> client.endTx())
        .onComplete(handleNoContentResponse(asyncResultHandler, id,  client,"Fiscal year {} {} deleted"))
    );
  }

  @Override
  @Validate
  public void putFinanceStorageFiscalYearsById(String id, String lang, FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FISCAL_YEAR_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  private Future<FiscalYear> saveFiscalYear(FiscalYear fiscalYear, DBClient client) {
    Promise<FiscalYear> future = Promise.promise();

    if (fiscalYear.getId() == null) {
      fiscalYear.setId(UUID.randomUUID().toString());
    }

    log.debug("Creating new fiscal year record with id={}", fiscalYear.getId());

   client.getPgClient().save(client.getConnection(), FISCAL_YEAR_TABLE, fiscalYear.getId(), fiscalYear, reply -> {
      if (reply.failed()) {
        handleFailure(future, reply);
      } else {
        log.info("New fiscal year record with id={} has been successfully created", fiscalYear.getId());
        future.complete(fiscalYear);
      }
    });
    return future.future();
  }

}
