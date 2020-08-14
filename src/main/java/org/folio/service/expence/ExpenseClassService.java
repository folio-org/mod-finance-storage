package org.folio.service.expence;

import static org.folio.rest.jaxrs.resource.FinanceStorageExpenseClasses.PostFinanceStorageExpenseClassesResponse.headersFor201;
import static org.folio.rest.util.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;

import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.jaxrs.resource.FinanceStorageExpenseClasses;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class ExpenseClassService {
  private static final String EXPENSE_CLASS_TABLE = "expense_class";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private PostgresClient pgClient;

  public ExpenseClassService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  public void createExpenseClass(ExpenseClass entity, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    vertxContext.runOnContext(v -> createExpenseClass(entity)
      .onSuccess(group -> {
        logger.debug("ExpenseClass with id {} created", entity.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          FinanceStorageExpenseClasses.PostFinanceStorageExpenseClassesResponse.respond201WithApplicationJson(group, headersFor201())));
      })
      .onFailure(throwable -> {
        logger.error("ExpenseClass creation with id {} failed", throwable, entity.getId());
        asyncResultHandler.handle(buildErrorResponse(throwable));
      }));
  }

  public void updateExpenseClass(String id, ExpenseClass entity, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    vertxContext.runOnContext(v -> updateExpenseClass(id, entity)
      .onSuccess(group -> {
        logger.debug("ExpenseClass with id {} updated", entity.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          FinanceStorageExpenseClasses.PutFinanceStorageExpenseClassesByIdResponse.respond204()));
      })
      .onFailure(throwable -> {
        logger.error("ExpenseClass update with id {} failed", throwable, entity.getId());
        asyncResultHandler.handle(buildErrorResponse(throwable));
      }));
  }

  private Future<ExpenseClass> createExpenseClass(ExpenseClass group) {
    Promise<ExpenseClass> promise = Promise.promise();
    if (group.getId() == null) {
      group.setId(UUID.randomUUID().toString());
    }
    pgClient.save(EXPENSE_CLASS_TABLE, group.getId(), group, reply -> {
      if (reply.failed()) {
        promise.fail(buildException(reply));
      }
      else {
        promise.complete(group);
      }
    });
    return promise.future();
  }

  private Future<ExpenseClass> updateExpenseClass(String id, ExpenseClass group) {
    Promise<ExpenseClass> promise = Promise.promise();
    pgClient.update(EXPENSE_CLASS_TABLE, JsonObject.mapFrom(group), id, reply -> {
      if (reply.failed()) {
        promise.fail(buildException(reply));
      }
      else if(reply.result().rowCount() == 0) {
        promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode()));
      }
      else {
        promise.complete(group);
      }
    });
    return promise.future();
  }

  private <T> HttpStatusException buildException(AsyncResult<T> reply) {
    String msg = PgExceptionUtil.badRequestMessage(reply.cause());
    String error = Optional.ofNullable(msg).map(this::buildFieldConstraintError).orElse(GENERIC_ERROR_CODE.getCode());
    if (GENERIC_ERROR_CODE.getCode().equals(error)) {
      return new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), error);
    }
    return new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), error);
  }

  private String buildFieldConstraintError(String msg) {
    final String ENTITY_NAME = "ExpenseClass";
    final String FIELD_CODE = "Code";
    final String FIELD_NAME = "Name";
    String uniqueConstraintName = HelperUtils.getSQLUniqueConstraintName(msg);
    if (uniqueConstraintName.contains(FIELD_CODE.toLowerCase())) {
      return JsonObject.mapFrom(HelperUtils.buildFieldConstraintError(ENTITY_NAME, FIELD_CODE)).encode();
    } else if (uniqueConstraintName.contains(FIELD_NAME.toLowerCase())) {
      return JsonObject.mapFrom(HelperUtils.buildFieldConstraintError(ENTITY_NAME, FIELD_NAME)).encode();
    }
    return JsonObject.mapFrom(GENERIC_ERROR_CODE.toError()).encode();
  }
}
