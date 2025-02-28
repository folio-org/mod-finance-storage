package org.folio.dao.jobnumber;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.JobNumber;
import org.folio.rest.persist.DBConn;

public class JobNumberPostgresDAO implements JobNumberDAO {

  private static final Logger log = LogManager.getLogger();
  private static final String JOB_NUMBER = "job_number";
  private static final String UPDATE_JOB_NUMBER_QUERY = "UPDATE " + JOB_NUMBER +
    " SET last_number = last_number + 1 WHERE type = $1 RETURNING last_number";

  @Override
  public Future<JobNumber> getNextJobNumber(String type, DBConn conn) {
    return conn.execute(UPDATE_JOB_NUMBER_QUERY, Tuple.of(type))
      .map(rowSet -> {
        if (rowSet.rowCount() == 0) {
          log.error("getNextJobNumber:: Could not get a new job number (rowCount is 0); type: {}", type);
          throw new HttpException(500, "Could not get a new job number (rowCount is 0)");
        }
        Row row = rowSet.iterator().next();
        return new JobNumber()
          .withSequenceNumber(row.getLong(0).toString())
          .withType(JobNumber.Type.fromValue(type));
      });
  }
}
