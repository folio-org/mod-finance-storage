package org.folio.utils;

import org.folio.CopilotGenerated;
import org.folio.config.SecureStoreConfiguration;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.tools.utils.TenantTool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.folio.config.SecureStoreType.AWS_SSM;
import static org.folio.config.SecureStoreType.VAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@CopilotGenerated(partiallyGenerated = true, model = "GPT-4o")
public class SecureStoreUtilsTest {

  @Test
  void testIsSecureStoreEnabled_withVault() {
    try (var mockedConfig = mockStatic(SecureStoreConfiguration.class)) {
      mockedConfig.when(SecureStoreConfiguration::getSecretStoreType).thenReturn(VAULT);

      var result = SecureStoreUtils.isSecureStoreEnabled();

      assertTrue(result);
      mockedConfig.verify(SecureStoreConfiguration::getSecretStoreType, times(1));
    }
  }

  @Test
  void testIsSecureStoreEnabled_withAwsSsm() {
    try (var mockedConfig = mockStatic(SecureStoreConfiguration.class)) {
      mockedConfig.when(SecureStoreConfiguration::getSecretStoreType).thenReturn(AWS_SSM);

      var result = SecureStoreUtils.isSecureStoreEnabled();

      assertTrue(result);
      mockedConfig.verify(SecureStoreConfiguration::getSecretStoreType, times(1));
    }
  }

  @Test
  void testBuildKey() {
    try (var mockedConfig = mockStatic(SecureStoreConfiguration.class);
         var mockedTenantTool = mockStatic(TenantTool.class)) {
      var envId = "testEnv";
      var tenantId = "testTenant";
      var property = "testProperty";
      var headers = Map.of("X-Okapi-Tenant", tenantId);
      var requestContext = mock(RequestContext.class);

      mockedConfig.when(SecureStoreConfiguration::getEnvId).thenReturn(envId);
      mockedTenantTool.when(() -> TenantTool.tenantId(headers)).thenReturn(tenantId);
      when(requestContext.getHeaders()).thenReturn(headers);

      var result = SecureStoreUtils.buildKey(property, requestContext);

      assertEquals("testEnv_testTenant_testProperty", result);
      mockedConfig.verify(SecureStoreConfiguration::getEnvId, times(1));
      mockedTenantTool.verify(() -> TenantTool.tenantId(headers), times(1));
    }
  }
}
