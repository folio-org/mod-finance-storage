package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageSettings;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class SettingsAPI implements FinanceStorageSettings {

  private static final String SETTINGS_TABLE = "settings";

  @Override
  @Validate
  public void getFinanceStorageSettings(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(SETTINGS_TABLE, Setting.class, SettingCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageSettingsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageSettings(Setting entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(SETTINGS_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageSettingsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageSettingsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(SETTINGS_TABLE, Setting.class, id, okapiHeaders, vertxContext, GetFinanceStorageSettingsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageSettingsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(SETTINGS_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageSettingsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageSettingsById(String id, Setting entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(SETTINGS_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageSettingsByIdResponse.class, asyncResultHandler);
  }
}
