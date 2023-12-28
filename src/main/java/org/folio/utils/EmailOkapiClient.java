package org.folio.utils;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.OkapiClient;

import io.vertx.core.Vertx;

public class EmailOkapiClient extends OkapiClient {

  private static final Logger logger = LogManager.getLogger(EmailOkapiClient.class);

  public EmailOkapiClient(String okapiUrl, Vertx vertx, Map<String, String> headers) {
    super(okapiUrl, vertx, headers);
  }

  public void sendEmail(String url, String data) {
    post(url, data)
      .onSuccess(response -> logger.info("POST {} complete successfully: {}", url, response))
      .onFailure(t -> logger.error("Email not delivered {}", t.getMessage()));
  }
}
