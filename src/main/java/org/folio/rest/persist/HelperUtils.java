package org.folio.rest.persist;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.folio.rest.persist.PgUtil.response;

public class HelperUtils {
  private static final Logger log = LoggerFactory.getLogger(HelperUtils.class);

  private HelperUtils() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static <T, E> void getEntitiesCollection(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, QueryHolder queryHolder, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Map<String, String> okapiHeaders) {
    String[] fieldList = {"*"};

    final Method respond500;

    try {
      respond500 = entitiesMetadataHolder.getRespond500WithTextPlainMethod();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return;
    }

    try {
      Method respond200 = entitiesMetadataHolder.getRespond200WithApplicationJson();
      Method respond400 = entitiesMetadataHolder.getRespond400WithTextPlainMethod();
      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
      postgresClient.get(queryHolder.getTable(), entitiesMetadataHolder.getClazz(), fieldList, queryHolder.buildCQLQuery(), true, false, reply -> {
        try {
          if (reply.succeeded()) {
            E collection = entitiesMetadataHolder.getCollectionClazz().newInstance();
            List<T> results = reply.result().getResults();
            Method setResults = entitiesMetadataHolder.getSetResultsMethod();
            Method setTotalRecordsMethod = entitiesMetadataHolder.getSetTotalRecordsMethod();
            setResults.invoke(collection, results);
            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
            setTotalRecordsMethod.invoke(collection, totalRecords);
            asyncResultHandler.handle(response(collection, respond200, respond500));
          } else {
            asyncResultHandler.handle(response(reply.cause().getLocalizedMessage(), respond400, respond500));
          }
        } catch (Exception e) {
          log.error(e.getMessage(), e);
          asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
        }
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }
}
