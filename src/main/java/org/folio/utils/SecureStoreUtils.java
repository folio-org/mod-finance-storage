package org.folio.utils;

import lombok.experimental.UtilityClass;
import org.folio.config.SecureStoreConfiguration;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.tools.utils.TenantTool;

import java.util.EnumSet;

import static org.folio.config.SecureStoreType.AWS_SSM;
import static org.folio.config.SecureStoreType.VAULT;

@UtilityClass
public class SecureStoreUtils {

  // We do not consider EPHEMERAL storage in this case because it can result in data loss on container restart
  // and because of this fact it is still better to store secrets into the DB before VAULT/AWS_SSM secure store is set
  public static boolean isSecureStoreEnabled() {
    return EnumSet.of(VAULT, AWS_SSM).contains(SecureStoreConfiguration.getSecretStoreType());
  }

  public static String buildKey(String property, RequestContext requestContext) {
    return "%s_%s_%s".formatted(SecureStoreConfiguration.getEnvId(), TenantTool.tenantId(requestContext.getHeaders()), property);
  }
}
