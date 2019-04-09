package org.folio.rest.persist;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
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

  private static final String POL_NUMBER_PREFIX = "polNumber_";
  private static final String QUOTES_SYMBOL = "\"";

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

  public static void respond(Handler<AsyncResult<Response>> handler, Response response) {
    AsyncResult<Response> result = Future.succeededFuture(response);
    handler.handle(result);
  }

  public enum SequenceQuery {

    CREATE_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "CREATE SEQUENCE IF NOT EXISTS " + constructSequenceName(purchaseOrderId) + " MINVALUE 1 MAXVALUE 999";
      }
    },
    GET_POL_NUMBER_FROM_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "SELECT * FROM NEXTVAL('" + constructSequenceName(purchaseOrderId) + "')";
      }
    },
    DROP_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "DROP SEQUENCE IF EXISTS " + constructSequenceName(purchaseOrderId);
      }
    };

    private static String constructSequenceName(String purchaseOrderId) {
      return QUOTES_SYMBOL + POL_NUMBER_PREFIX + purchaseOrderId + QUOTES_SYMBOL;
    }

    public abstract String getQuery(String purchaseOrderId);
  }

}
