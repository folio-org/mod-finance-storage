package org.folio.rest.impl;

import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildOkResponse;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.folio.dao.fiscalyearhierarchy.FiscalYearHierarchyDAO;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYearHierarchy;
import org.folio.rest.persist.DBClient;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class FiscalYearHierarchyApi implements FinanceStorageFiscalYearHierarchy {

  @Autowired
  private FiscalYearHierarchyDAO fiscalYearHierarchyDAO;

  public FiscalYearHierarchyApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getFinanceStorageFiscalYearHierarchy(String fiscalYearId, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (StringUtils.isBlank(fiscalYearId)) {
      asyncResultHandler.handle(buildErrorResponse(new HttpException(400, "fiscalYearId is required")));
      return;
    }

    new DBClient(vertxContext, okapiHeaders)
      .withConn(conn -> fiscalYearHierarchyDAO.getFiscalYearHierarchy(conn, fiscalYearId))
      .onComplete(hierarchy -> {
        if (hierarchy.succeeded()) {
          asyncResultHandler.handle(buildOkResponse(hierarchy.result()));
        } else {
          asyncResultHandler.handle(buildErrorResponse(hierarchy.cause()));
        }
      });
  }

}
