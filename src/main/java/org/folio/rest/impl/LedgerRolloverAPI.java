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

  private static final Logger log = LogManager.getLogger(LedgerRolloverAPI.class);

  @Autowired
  private LedgerRolloverService ledgerRolloverService;

  public LedgerRolloverAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRollovers(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, LedgerFiscalYearRollover.class, LedgerFiscalYearRolloverCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageLedgerRolloversResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgerRollovers(String lang, LedgerFiscalYearRollover entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    ledgerRolloverService.rolloverLedger(entity, new RequestContext(vertxContext, okapiHeaders))
            .onComplete(result -> {
              if (result.failed()) {
                HttpException cause = (HttpException) result.cause();
                log.error("Update of the fund record {} has failed", entity.getId(), cause);
                HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
              } else {
                asyncResultHandler.handle(succeededFuture(buildResponseWithLocation(okapiHeaders.get(OKAPI_URL), "/finance-storage/ledger-rollover", entity)));
              }
            });

  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, LedgerFiscalYearRollover.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgerRolloversByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgerRolloversById(String rolloverId, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    ledgerRolloverService.deleteRollover(rolloverId, new RequestContext(vertxContext, okapiHeaders))
      .onComplete(result -> {
        if (result.failed()) {
          HttpException cause = (HttpException) result.cause();
          log.error("Rollover deletion error {}", rolloverId, cause);
          HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
        } else {
          asyncResultHandler.handle(buildNoContentResponse());
        }
      });
  }

  @Override
  @Validate
  public void putFinanceStorageLedgerRolloversById(String id, String lang, LedgerFiscalYearRollover entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageLedgerRolloversByIdResponse.class, asyncResultHandler);
  }
}
