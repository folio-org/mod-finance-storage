package org.folio.dao.jobnumber;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.JobNumber;
import org.folio.rest.persist.DBConn;

public interface JobNumberDAO {
  Future<JobNumber> getNextJobNumber(String type, DBConn conn);
}
