package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
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
    PgUtil.post(FUND_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundsResponse.class, asyncResultHandler);
  }

  @Override
  public void postFinanceStorageFundsBatch(BatchIdCollection entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new RequestContext(vertxContext, okapiHeaders).toDBClient()
      .withConn(conn -> fundService.getFundsByIds(entity.getIds(), conn)
        .map(funds -> new FundCollection().withFunds(funds).withTotalRecords(funds.size()))
        .onSuccess(funds -> asyncResultHandler.handle(succeededFuture(respond200WithApplicationJson(funds))))
        .onFailure(throwable -> {
          HttpException cause = (HttpException) throwable;
          logger.error("Failed to get funds by ids {}", entity.getIds(), cause);
          HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
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
          HttpException cause = (HttpException) throwable;
          logger.error("Failed to update the finance storage fund with Id {}", fund.getId(), cause);
          HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
        })
    );
  }
}
