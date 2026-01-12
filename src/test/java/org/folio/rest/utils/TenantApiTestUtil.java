package org.folio.rest.utils;

import static org.folio.StorageTestSuite.URL_TO_HEADER;
import static org.folio.rest.impl.TenantReferenceAPI.LOAD_SYNC_PARAMETER;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;

import io.restassured.http.Header;
import org.folio.rest.tools.utils.ModuleName;

public class TenantApiTestUtil {

  private static final int TENANT_OP_WAITINGTIME = 60000;

  private TenantApiTestUtil() {

  }

  public static TenantAttributes prepareTenantBody(boolean isLoadSampleData, boolean isLoadReferenceData) {
    List<Parameter> parameters = new ArrayList<>();
    parameters.add(new Parameter().withKey("loadReference").withValue(String.valueOf(isLoadReferenceData)));
    parameters.add(new Parameter().withKey("loadSample").withValue(String.valueOf(isLoadSampleData)));
    parameters.add(new Parameter().withKey(LOAD_SYNC_PARAMETER).withValue("true"));

    String moduleName = ModuleName.getModuleName();
    return new TenantAttributes()
      .withModuleTo(moduleName + "-999.0.0")
      .withModuleFrom(moduleName + "-0.0.0")
      .withParameters(parameters);
  }

  public static TenantJob prepareTenant(Header tenantHeader, boolean isLoadSampleData, boolean isLoadReferenceData) {
    TenantAttributes tenantAttributes = prepareTenantBody(isLoadSampleData, isLoadReferenceData);

    return postTenant(tenantHeader, tenantAttributes);
  }

  public static TenantJob postTenant(Header tenantHeader, TenantAttributes tenantAttributes) {
    CompletableFuture<TenantJob> future = new CompletableFuture<>();
    TenantClient tClient =  new TenantClient(URL_TO_HEADER.getValue(), tenantHeader.getValue(), null);
    try {
      tClient.postTenant(tenantAttributes, event -> {
        if (event.failed()) {
          future.completeExceptionally(event.cause());
        } else {
          TenantJob tenantJob = event.result().bodyAsJson(TenantJob.class);
          tClient.getTenantByOperationId(tenantJob.getId(), TENANT_OP_WAITINGTIME, result -> {
            if(result.failed()) {
              future.completeExceptionally(result.cause());
            } else {
              // Add delay to ensure async database operations complete and are visible
              try {
                Thread.sleep(500);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              future.complete(tenantJob);
            }
          });
        }
      });
      return future.get(60, TimeUnit.SECONDS);
    } catch (Exception e) {
      fail(e);
      return null;
    }
  }

  public static void deleteTenant(TenantJob tenantJob, Header tenantHeader) {
    TenantClient tenantClient = new TenantClient(URL_TO_HEADER.getValue(), tenantHeader.getValue(), null);

    if (tenantJob != null) {
      CompletableFuture<Void> completableFuture = new CompletableFuture<>();
      tenantClient.deleteTenantByOperationId(tenantJob.getId(), event -> {
        if (event.failed()) {
          completableFuture.completeExceptionally(event.cause());
        } else {
          completableFuture.complete(null);
        }
      });
      try {
        completableFuture.get(60, TimeUnit.SECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        fail(e);
      }

    }

  }

  public static void purge(Header tenantHeader) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    TenantClient tClient =  new TenantClient(URL_TO_HEADER.getValue(), tenantHeader.getValue(), null);
    TenantAttributes tenantAttributes = prepareTenantBody(false, false).withPurge(true);
    try {
      tClient.postTenant(tenantAttributes, event -> {
        if (event.failed()) {
          future.completeExceptionally(event.cause());
        } else {
          future.complete(null);
        }
      });
      future.get(60, TimeUnit.SECONDS);
    } catch (Exception e) {
      fail(e);
    }
  }
}
