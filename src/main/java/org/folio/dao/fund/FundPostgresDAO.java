package org.folio.dao.fund;

import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.List;

import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class FundPostgresDAO implements FundDAO {

  public Future<List<Fund>> getFunds(Criterion criterion, DBClient client) {
    Promise<List<Fund>> promise = Promise.promise();
    client.getPgClient().get(FUND_TABLE, Fund.class, criterion, false, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        List<Fund> funds = reply.result()
          .getResults();
        promise.complete(funds);
      }
    });
    return promise.future();
  }
}
