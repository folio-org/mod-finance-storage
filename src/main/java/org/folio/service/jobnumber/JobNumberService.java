package org.folio.service.jobnumber;

import java.util.Objects;

import io.vertx.core.Future;
import org.folio.dao.jobnumber.JobNumberDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.persist.DBClient;

public class JobNumberService {

  private final JobNumberDAO jobNumberDAO;

  public JobNumberService(JobNumberDAO jobNumberDAO) {
    this.jobNumberDAO = jobNumberDAO;
  }

  public Future<SequenceNumber> getNextNumber(String type, RequestContext requestContext) {
    if (!Objects.equals(type, "Logs")) {
      return Future.failedFuture(new IllegalArgumentException("Only 'Logs' type is supported"));
    }
    var dbClient = new DBClient(requestContext);
    return dbClient.withConn(conn -> jobNumberDAO.getNextJobNumber(type, conn));
  }
}
