package org.folio.config;

import io.vertx.junit5.VertxExtension;
import org.folio.CopilotGenerated;
import org.folio.tools.store.impl.AwsStore;
import org.folio.tools.store.impl.EphemeralStore;
import org.folio.tools.store.impl.FsspStore;
import org.folio.tools.store.impl.VaultStore;
import org.folio.tools.store.properties.AwsConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import static org.folio.config.SecureStoreConfiguration.SECRET_STORE_TYPE;
import static org.folio.config.SecureStoreType.AWS_SSM;
import static org.folio.config.SecureStoreType.EPHEMERAL;
import static org.folio.config.SecureStoreType.VAULT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

import uk.org.webcompere.systemstubs.jupiter.SystemStub;

@CopilotGenerated(partiallyGenerated = true, model = "GPT-4.1")
@ExtendWith({VertxExtension.class, SystemStubsExtension.class})
public class SecureStoreConfigurationTest {

  @SystemStub
  private EnvironmentVariables environmentVariables;

  @Test
  void testGetSecureStoreByType_Ephemeral() {
    environmentVariables.set(SECRET_STORE_TYPE, EPHEMERAL.getValue());

    var config = new SecureStoreConfiguration();
    try (MockedStatic<EphemeralStore> mocked = mockStatic(EphemeralStore.class)) {
      var mockStore = mock(EphemeralStore.class);
      mocked.when(() -> EphemeralStore.create(any())).thenReturn(mockStore);

      var result = config.getSecureStoreByType();
      assertSame(mockStore, result);
    }
  }

  @Test
  void testGetSecureStoreByType_Aws() {
    environmentVariables.set(SECRET_STORE_TYPE, AWS_SSM.getValue());
    environmentVariables.set("SECRET_STORE_AWS_SSM_REGION", "us-east-1");
    environmentVariables.set("SECRET_STORE_AWS_SSM_USE_IAM", "true");

    var config = new SecureStoreConfiguration();
    try (MockedStatic<AwsStore> mocked = mockStatic(AwsStore.class)) {
      var mockStore = mock(AwsStore.class);
      mocked.when(() -> AwsStore.create((AwsConfigProperties) any())).thenReturn(mockStore);

      var result = config.getSecureStoreByType();
      assertSame(mockStore, result);
    }
  }

  @Test
  void testGetSecureStoreByType_Vault() {
    environmentVariables.set(SECRET_STORE_TYPE, VAULT.getValue());
    environmentVariables.set("SECRET_STORE_VAULT_TOKEN", "token");
    environmentVariables.set("SECRET_STORE_VAULT_ADDRESS", "address");

    var config = new SecureStoreConfiguration();
    try (MockedStatic<VaultStore> mocked = mockStatic(VaultStore.class)) {
      var mockStore = mock(VaultStore.class);
      mocked.when(() -> VaultStore.create(any())).thenReturn(mockStore);

      var result = config.getSecureStoreByType();
      assertSame(mockStore, result);
    }
  }

  @Test
  void testCreateFssStore() {
    environmentVariables.set(SECRET_STORE_TYPE, "FSSP");
    environmentVariables.set("SECRET_STORE_FSSP_ADDRESS", "http://fssp");
    environmentVariables.set("SECRET_STORE_FSSP_ENABLE_SSL", "true");
    environmentVariables.set("SECRET_STORE_FSSP_SECRET_PATH", "/secret");
    environmentVariables.set("SECRET_STORE_FSSP_TRUSTSTORE_PATH", "/truststore");
    environmentVariables.set("SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE", "JKS");
    environmentVariables.set("SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD", "password");

    var config = new SecureStoreConfiguration();
    try (MockedConstruction<FsspStore> mocked = mockConstruction(FsspStore.class)) {
      var result = config.createFssStore();
      assertSame(mocked.constructed().getFirst(), result);
    }
  }

  @Test
  void testGetEnvIdReturnsDefaultWhenEnvNotSet() {
    environmentVariables.set(SecureStoreConfiguration.ENV, null);

    var result = SecureStoreConfiguration.getEnvId();
    assertEquals(SecureStoreConfiguration.DEFAULT_ENV_ID, result);
  }

  @Test
  void testGetEnvIdReturnsEnvValue() {
    environmentVariables.set(SecureStoreConfiguration.ENV, "custom");

    var result = SecureStoreConfiguration.getEnvId();
    assertEquals("custom", result);
  }

  @Test
  void testGetSecretStoreTypeReturnsDefault() {
    environmentVariables.set(SECRET_STORE_TYPE, null);

    var result = SecureStoreConfiguration.getSecretStoreType();
    assertEquals(EPHEMERAL, result);
  }

  @Test
  void testGetSecretStoreTypeReturnsValue() {
    environmentVariables.set(SECRET_STORE_TYPE, VAULT.getValue());

    var result = SecureStoreConfiguration.getSecretStoreType();
    assertEquals(VAULT, result);
  }
}
