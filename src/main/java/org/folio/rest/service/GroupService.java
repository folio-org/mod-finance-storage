package org.folio.rest.service;

import static org.folio.rest.jaxrs.resource.FinanceStorageGroups.PostFinanceStorageGroupsResponse.headersFor201;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.utils.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.rest.utils.ErrorCodes.UNIQUE_FIELD_CONSTRAINT_ERROR;

import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
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

public class GroupService {
  private static final String GROUPS_TABLE = "groups";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private PostgresClient pgClient;

  public GroupService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  public void createGroup(Group entity, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    vertxContext.runOnContext(v -> {
      createGroup(entity)
        .onSuccess(group -> {
          logger.debug("Group wit id {} created", entity.getId());
          asyncResultHandler.handle(Future.succeededFuture(
              FinanceStorageGroups.PostFinanceStorageGroupsResponse.respond201WithApplicationJson(group, headersFor201())));
        })
        .onFailure(throwable -> {
          logger.error("Group creation with id {} failed", throwable, entity.getId());
          asyncResultHandler.handle(buildErrorResponse(throwable));
        });
    });
  }

  public Future<Group> createGroup(Group group) {
    Promise<Group> promise = Promise.promise();
    if (group.getId() == null) {
      group.setId(UUID.randomUUID().toString());
    }
    pgClient.save(GROUPS_TABLE, group.getId(), group, reply -> {
      if (reply.failed()) {
        promise.fail(createException(reply));
      } else {
        promise.complete(group);
      }
    });
    return promise.future();
  }

  private HttpStatusException createException(AsyncResult<String> reply) {
    String msg = PgExceptionUtil.badRequestMessage(reply.cause());
    String error = Optional.ofNullable(msg).map(this::buildFieldConstraintError).orElse(GENERIC_ERROR_CODE.getCode());
    return new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), error);
  }

  private String buildFieldConstraintError(String msg) {
    final String FIELD_CODE = "code";
    final String FIELD_NAME = "name";
    String uniqueConstraintName = HelperUtils.getSQLUniqueConstraintName(msg);
    if (uniqueConstraintName.contains(FIELD_CODE)) {
      return JsonObject.mapFrom(HelperUtils.buildFieldConstraintError(FIELD_CODE)).encode();
    } else if (uniqueConstraintName.contains(FIELD_NAME)) {
      return JsonObject.mapFrom(HelperUtils.buildFieldConstraintError(FIELD_NAME)).encode();
    }
    return JsonObject.mapFrom(GENERIC_ERROR_CODE.toError()).encode();
  }
}
