package org.folio.service.group;

import static org.folio.rest.jaxrs.resource.FinanceStorageGroups.PostFinanceStorageGroupsResponse.headersFor201;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;

import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.builders.error.NameCodeConstraintErrorBuilder;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.rest.exception.HttpException;

public class GroupService {

  private static final Logger logger = LogManager.getLogger(GroupService.class);

  private static final String GROUPS_TABLE = "groups";

  private PostgresClient pgClient;
  private final NameCodeConstraintErrorBuilder nameCodeConstraintErrorBuilder;

  public GroupService(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
    this.nameCodeConstraintErrorBuilder = new NameCodeConstraintErrorBuilder();
  }

  public void createGroup(Group entity, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    logger.debug("createGroup:: Trying to create group");
    vertxContext.runOnContext(v -> createGroup(entity)
      .onSuccess(group -> {
        logger.info("createGroup:: Group with id {} created", entity.getId());
        asyncResultHandler.handle(Future.succeededFuture(
            FinanceStorageGroups.PostFinanceStorageGroupsResponse.respond201WithApplicationJson(group, headersFor201())));
      })
      .onFailure(throwable -> {
        logger.error("createGroup:: Group creation with id {} failed", entity.getId(), throwable);
        asyncResultHandler.handle(buildErrorResponse(throwable));
      }));
  }

  public void updateGroup(Group entity, String id, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    logger.debug("createGroup:: Trying to update group with id {}", id);
    vertxContext.runOnContext(v -> updateGroup(entity, id)
      .onSuccess(group -> {
        logger.info("updateGroup:: Group with id {} updated", entity.getId());
        asyncResultHandler.handle(Future.succeededFuture(
          FinanceStorageGroups.PutFinanceStorageGroupsByIdResponse.respond204()));
      })
      .onFailure(throwable -> {
        logger.error("Group update with id {} failed", entity.getId(), throwable);
        asyncResultHandler.handle(buildErrorResponse(throwable));
      }));
  }

  private Future<Group> createGroup(Group group) {
    Promise<Group> promise = Promise.promise();
    if (group.getId() == null) {
      group.setId(UUID.randomUUID().toString());
    }

    pgClient.save(GROUPS_TABLE, group.getId(), group, reply -> {
      if (reply.failed()) {
        promise.fail(nameCodeConstraintErrorBuilder.buildException(reply, Group.class));
      }
      else {
        promise.complete(group);
      }
    });
    return promise.future();
  }

  private Future<Group> updateGroup(Group group, String id) {
    Promise<Group> promise = Promise.promise();
    pgClient.update(GROUPS_TABLE, JsonObject.mapFrom(group), id, reply -> {
      if (reply.failed()) {
        promise.fail(nameCodeConstraintErrorBuilder.buildException(reply, Group.class));
      }
      else if(reply.result().rowCount() == 0) {
        promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "Group not found for update, id=" + id));
      }
      else {
        promise.complete(group);
      }
    });
    return promise.future();
  }
}
