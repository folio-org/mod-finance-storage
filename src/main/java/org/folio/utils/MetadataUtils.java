package org.folio.utils;

import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Metadata;

import java.util.Date;
import java.util.Map;

public class MetadataUtils {

  public static Metadata generateMetadata(Map<String, String> okapiHeaders) {
    Date now = new Date();
    String userId = getUserId(okapiHeaders);
    return new Metadata()
      .withUpdatedDate(now)
      .withCreatedDate(now)
      .withCreatedByUserId(userId)
      .withUpdatedByUserId(userId);
  }

  private static String getUserId(Map<String, String> okapiHeaders) {
    // NOTE: Okapi usually populates metadata when a POST method is used with an entity with metadata.
    // But in the case of the batch API there is no top-level metadata, so it is not populated automatically.
    // This code comes from Okapi, the exception is not logged on purpose (null should be used for the user id
    // if we have no way to tell what it should be).
    String userId = okapiHeaders.get(XOkapiHeaders.USER_ID);
    if (userId == null) {
      try {
        userId = (new OkapiToken(okapiHeaders.get(XOkapiHeaders.TOKEN))).getUserIdWithoutValidation();
      } catch (Exception ignored) {
        // could not find user id - ignoring
      }
    }
    return userId;
  }
}
