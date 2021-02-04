package org.folio.service.expence;

import static org.folio.rest.jaxrs.resource.FinanceStorageExpenseClasses.PostFinanceStorageExpenseClassesResponse.headersFor201;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;

import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.builders.error.NameCodeConstraintErrorBuilder;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.jaxrs.resource.FinanceStorageExpenseClasses;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class ExpenseClassService {
  private static final String EXPENSE_CLASS_TABLE = "expense_class";

  private final Logger logger = LogManager.getLogger(this.getClass());
  private final PostgresClient pgClient;
  private final NameCodeConstraintErrorBuilder nameCodeConstraintErrorBuilder;

  public ExpenseClassService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
    this.nameCodeConstraintErrorBuilder = new NameCodeConstraintErrorBuilder();
  }

  public void createExpenseClass(ExpenseClass entity, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    vertxContext.runOnContext(v -> createExpenseClass(entity)
      .onSuccess(group -> {
        logger.debug("ExpenseClass with id {} created", entity.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          FinanceStorageExpenseClasses.PostFinanceStorageExpenseClassesResponse.respond201WithApplicationJson(group, headersFor201())));
      })
      .onFailure(throwable -> {
        logger.error("ExpenseClass creation with id {} failed", entity.getId(), throwable);
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
        logger.error("ExpenseClass update with id {} failed", entity.getId(), throwable);
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
        promise.fail(nameCodeConstraintErrorBuilder.buildException(reply, ExpenseClass.class));
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
        promise.fail(nameCodeConstraintErrorBuilder.buildException(reply, ExpenseClass.class));
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
}
