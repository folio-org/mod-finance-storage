package org.folio.rest.impl;

import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildOkResponse;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.jaxrs.resource.FinanceStorageJobNumber;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

public class JobNumberAPI implements FinanceStorageJobNumber {

  private static final Logger log = LogManager.getLogger(JobNumberAPI.class);
  private static final String JOB_NUMBER_QUERY = "SELECT nextval('job_number')";

  @Override
  public void getFinanceStorageJobNumber(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    log.debug("Trying to get job number");
    vertxContext.runOnContext((Void v) -> {
      String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      PostgresClient.getInstance(vertxContext.owner(), tenantId).selectSingle(JOB_NUMBER_QUERY, reply -> {
        try {
          if(reply.succeeded()) {
            String jobNumber = reply.result().getLong(0).toString();
            log.info("Retrieved job number: {}", jobNumber);
            asyncResultHandler.handle(buildOkResponse(new SequenceNumber().withSequenceNumber(jobNumber)));
          } else {
            throw new IllegalArgumentException("Unable to generate job number from sequence", reply.cause());
          }
        } catch (Exception e) {
          log.error("Error while handling response for job number request", e);
          asyncResultHandler.handle(buildErrorResponse(e));
        }
      });
    });
  }
}
