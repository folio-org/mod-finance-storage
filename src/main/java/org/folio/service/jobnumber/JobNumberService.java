package org.folio.service.jobnumber;

import io.vertx.core.Future;
import org.folio.dao.jobnumber.JobNumberDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.JobNumber;
import org.folio.rest.persist.DBClient;

public class JobNumberService {

  private final JobNumberDAO jobNumberDAO;

  public JobNumberService(JobNumberDAO jobNumberDAO) {
    this.jobNumberDAO = jobNumberDAO;
  }

  public Future<JobNumber> getNextNumber(String type, RequestContext requestContext) {
    return validateType(type)
      .compose(v -> {
        var dbClient = new DBClient(requestContext);
        return dbClient.withTrans(conn -> jobNumberDAO.getNextJobNumber(type, conn));
      });
  }

  private Future<Void> validateType(String type) {
    try {
      JobNumber.Type.fromValue(type);
      return Future.succeededFuture();
    } catch (IllegalArgumentException e) {
      return Future.failedFuture(new IllegalArgumentException("Only type is supported"));
    }
  }
}
