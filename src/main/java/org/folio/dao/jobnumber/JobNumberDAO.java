package org.folio.dao.jobnumber;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.SequenceNumber;
import org.folio.rest.persist.DBConn;

public interface JobNumberDAO {
  Future<SequenceNumber> getNextJobNumber(String type, DBConn conn);
}
