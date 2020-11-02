package org.folio.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.folio.rest.persist.DBClient;

public class RolloverPOC {

   public Future<Void> runFunction(String ledgerId, DBClient dbClient) {
       Promise<Void> promise = Promise.promise();
       String sql = "DO\n" +
               "$$\n" +
               "begin\n" +
               "     PERFORM %s_mod_finance_storage.budget_encumbrances_rollover('%s');\n" +
               "end;\n" +
               "$$ LANGUAGE plpgsql;";

       dbClient.getPgClient().execute(String.format(sql, dbClient.getTenantId(), ledgerId), event -> {
           if (event.succeeded()) {
               promise.complete();
           } else {
               promise.fail(event.cause());
           }
       });
       return promise.future();
   }
}
