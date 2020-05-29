package org.folio.rest.persist;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.folio.rest.persist.PgUtil.response;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ErrorCodes.UNIQUE_FIELD_CONSTRAINT_ERROR;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public final class HelperUtils {
  private HelperUtils() { }

  public static final String ID_FIELD_NAME = "id";

  public static String getEndpoint(Class<?> clazz) {
    return clazz.getAnnotation(Path.class).value();
  }

  public static Future<Void> deleteRecordById(String id, DBClient client, String table) {
    Promise<Void> promise = Promise.promise();

    client.getPgClient().delete(client.getConnection(), table, id, reply -> {
      if(reply.failed()) {
        handleFailure(promise, reply);
      } else if (reply.result().getUpdated() == 0) {
        promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode()));
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }

  public static void replyWithErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, HttpStatusException cause) {
    asyncResultHandler.handle(succeededFuture(Response.status(cause.getStatusCode())
      .entity(Optional.of(cause).map(HttpStatusException::getPayload).orElse(cause.getMessage()))
      .header(CONTENT_TYPE, MediaType.TEXT_PLAIN)
      .build()));
  }

  public static <T, E> void getEntitiesCollectionWithDistinctOn(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, QueryHolder queryHolder, String sortField, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Map<String, String> okapiHeaders) {
    Method respond500 = getRespond500(entitiesMetadataHolder, asyncResultHandler);
    Method respond400 = getRespond400(entitiesMetadataHolder, asyncResultHandler);
    try {
      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
      postgresClient.get(queryHolder.getTable(), entitiesMetadataHolder.getClazz(), QueryHolder.JSONB, queryHolder.buildCQLQuery(), true, false, null, sortField,
        reply -> processDbQueryReply(entitiesMetadataHolder, asyncResultHandler, respond500, respond400, reply));
    } catch (CQLQueryValidationException e) {

      asyncResultHandler.handle(response(e.getMessage(), respond400, respond500));
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  private static Method getRespond500(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond500WithTextPlainMethod();
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return null;
    }
  }

  private static Method getRespond400(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond400WithTextPlainMethod();
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return null;
    }
  }

  private static <T, E> void processDbQueryReply(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler, Method respond500, Method respond400, AsyncResult<Results<T>> reply) {
    try {
      Method respond200 = entitiesMetadataHolder.getRespond200WithApplicationJson();
      if (reply.succeeded()) {
        E collection = entitiesMetadataHolder.getCollectionClazz().getConstructor().newInstance();
        List<T> results = reply.result().getResults();
        Method setResults =  entitiesMetadataHolder.getSetResultsMethod();
        Method setTotalRecordsMethod =  entitiesMetadataHolder.getSetTotalRecordsMethod();
        setResults.invoke(collection, results);
        Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
        setTotalRecordsMethod.invoke(collection, totalRecords);
        asyncResultHandler.handle(response(collection, respond200, respond500));
      } else {
        asyncResultHandler.handle(response(reply.cause().getLocalizedMessage(), respond400, respond500));
      }
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  public static String getFullTableName(String tenantId, String tableName) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + tableName;
  }

  public static String getSQLUniqueConstraintName(String errorMessage){
    if (!StringUtils.isEmpty(errorMessage)) {
      Pattern pattern = Pattern.compile("(unique constraint)\\s+\"(?<constraint>.*?)\"");
      Matcher matcher = pattern.matcher(errorMessage);
      if (matcher.find()) {
        return matcher.group("constraint");
      }
    }
    return StringUtils.EMPTY;
  }

  public static Error buildFieldConstraintError(String entityName, String fieldName) {
    final String FIELD_NAME = "field";
    final String ENTITY_NAME = "entity";
    String description = MessageFormat.format(UNIQUE_FIELD_CONSTRAINT_ERROR.getDescription(), fieldName);
    String code = MessageFormat.format(UNIQUE_FIELD_CONSTRAINT_ERROR.getCode(), entityName, fieldName);
    Error error =new Error().withCode(code).withMessage(description);
    error.getParameters().add(new Parameter().withKey(FIELD_NAME).withValue(fieldName));
    error.getParameters().add(new Parameter().withKey(ENTITY_NAME).withValue(entityName));
    return error;
  }

  public static String getQueryValues(List<JsonObject> entities) {
    return entities.stream().map(entity -> "('" + entity.getString("id") + "', '" + entity.encode() + "'::json)").collect(Collectors.joining(","));
  }

}
