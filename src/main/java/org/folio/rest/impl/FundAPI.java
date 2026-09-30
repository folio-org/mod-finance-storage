package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.rest.jaxrs.resource.FinanceStorageFunds.PostFinanceStorageFundsBatchResponse.respond200WithApplicationJson;
import static org.folio.rest.jaxrs.resource.FinanceStorageGroupFundFiscalYears.PutFinanceStorageGroupFundFiscalYearsByIdResponse.respond204;

import javax.ws.rs.core.Response;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.BatchIdCollection;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.service.fund.FundService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class FundAPI implements FinanceStorageFunds {

  private static final Logger logger = LogManager.getLogger(FundAPI.class);

  public static final String FUND_TABLE = "fund";
  private static final String FUND_LOCATION_PREFIX = "/finance-storage/funds/";

  @Autowired
  private FundService fundService;

  public FundAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageFunds(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FUND_TABLE, Fund.class, FundCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageFundsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFunds(Fund entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.debug("Trying to create a finance storage fund");
    fundService.createFund(entity, new RequestContext(vertxContext, okapiHeaders))
      .onSuccess(fund -> asyncResultHandler.handle(succeededFuture(
        PostFinanceStorageFundsResponse.respond201WithApplicationJson(fund,
          PostFinanceStorageFundsResponse.headersFor201().withLocation(FUND_LOCATION_PREFIX + fund.getId())))))
      .onFailure(throwable -> {
        logger.error("Failed to create the finance storage fund with Id {}", entity.getId(), throwable);
        replyWithErrorResponse(asyncResultHandler, throwable);
      });
  }

  @Override
  public void postFinanceStorageFundsBatch(BatchIdCollection entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new RequestContext(vertxContext, okapiHeaders).toDBClient()
      .withConn(conn -> fundService.getFundsByIds(entity.getIds(), conn)
        .map(funds -> new FundCollection().withFunds(funds).withTotalRecords(funds.size()))
        .onSuccess(funds -> asyncResultHandler.handle(succeededFuture(respond200WithApplicationJson(funds))))
        .onFailure(throwable -> {
          logger.error("Failed to get funds by ids {}", entity.getIds(), throwable);
          replyWithErrorResponse(asyncResultHandler, throwable);
        }));
  }

  @Override
  @Validate
  public void getFinanceStorageFundsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_TABLE, Fund.class, id, okapiHeaders, vertxContext, GetFinanceStorageFundsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFundsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundsById(String id, Fund fund, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.debug("Trying to update finance storage fund by id {}", id);
    fund.setId(id);
    vertxContext.runOnContext(event ->
      fundService.updateFund(fund, new RequestContext(vertxContext, okapiHeaders))
        .onSuccess(result -> asyncResultHandler.handle(succeededFuture(respond204())))
        .onFailure(throwable -> {
          logger.error("Failed to update the finance storage fund with Id {}", fund.getId(), throwable);
          replyWithErrorResponse(asyncResultHandler, throwable);
        })
    );
  }

  private void replyWithErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, Throwable throwable) {
    var cause = throwable instanceof HttpException httpException
      ? httpException
      : new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(), throwable.getMessage());
    HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
  }
}
