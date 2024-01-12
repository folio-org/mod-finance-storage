package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.rollover.LedgerFiscalYearRolloverDAO.LEDGER_FISCAL_YEAR_ROLLOVER_TABLE;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.util.ResponseUtils.buildNoContentResponse;
import static org.folio.rest.util.ResponseUtils.buildResponseWithLocation;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerRollovers;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.service.rollover.LedgerRolloverService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class LedgerRolloverAPI implements FinanceStorageLedgerRollovers {

  private static final Logger logger = LogManager.getLogger(LedgerRolloverAPI.class);

  @Autowired
  private LedgerRolloverService ledgerRolloverService;

  public LedgerRolloverAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRollovers(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, LedgerFiscalYearRollover.class, LedgerFiscalYearRolloverCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageLedgerRolloversResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgerRollovers(LedgerFiscalYearRollover entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.debug("Trying to create finance storage ledger rollover");
    ledgerRolloverService.rolloverLedger(entity, new RequestContext(vertxContext, okapiHeaders))
      .onComplete(result -> {
        if (result.failed()) {
          Throwable t = result.cause();
          if (!(t instanceof HttpException)) {
            t = new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), t);
          }
          HttpException cause = (HttpException)t;
          if (Response.Status.CONFLICT.getStatusCode() == cause.getStatusCode()) {
            logger.error("Rollover already exists with id {}", entity.getId(), cause);
          } else {
            logger.error("Creating rollover with id {} failed", entity.getId(), cause);
          }
          HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
        } else {
          asyncResultHandler.handle(succeededFuture(buildResponseWithLocation(okapiHeaders.get(OKAPI_URL), "/finance-storage/ledger-rollover", entity)));
        }
      });
  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversById(String id, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, LedgerFiscalYearRollover.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgerRolloversByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgerRolloversById(String rolloverId, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.debug("Trying to delete finance storage ledger rollover by id {}", rolloverId);
    ledgerRolloverService.deleteRollover(rolloverId, new RequestContext(vertxContext, okapiHeaders))
      .onComplete(result -> {
        if (result.failed()) {
          HttpException cause = (HttpException) result.cause();
          logger.error("Rollover deletion error {}", rolloverId, cause);
          HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
        } else {
          asyncResultHandler.handle(buildNoContentResponse());
        }
      });
  }

  @Override
  @Validate
  public void putFinanceStorageLedgerRolloversById(String id, LedgerFiscalYearRollover entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageLedgerRolloversByIdResponse.class, asyncResultHandler);
  }
}
