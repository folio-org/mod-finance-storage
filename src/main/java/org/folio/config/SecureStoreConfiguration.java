package org.folio.config;

import lombok.extern.log4j.Log4j2;
import org.folio.tools.store.SecureStore;
import org.folio.tools.store.impl.AwsStore;
import org.folio.tools.store.impl.EphemeralStore;
import org.folio.tools.store.impl.FsspStore;
import org.folio.tools.store.impl.VaultStore;
import org.folio.tools.store.properties.AwsConfigProperties;
import org.folio.tools.store.properties.EphemeralConfigProperties;
import org.folio.tools.store.properties.FsspConfigProperties;
import org.folio.tools.store.properties.VaultConfigProperties;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.folio.config.SecureStoreType.EPHEMERAL;
import static org.folio.tools.store.properties.VaultConfigProperties.DEFAULT_VAULT_SECRET_ROOT;

@Log4j2
public class SecureStoreConfiguration {

  private static final String NOT_FOUND_MESSAGE = "Failed to find required config property: %s";

  public static final String ENV = "ENV";
  public static final String DEFAULT_ENV_ID = "folio";
  public static final String SECRET_STORE_TYPE = "SECRET_STORE_TYPE";

  private static final String SECRET_STORE_VAULT_TOKEN = "SECRET_STORE_VAULT_TOKEN";
  private static final String SECRET_STORE_VAULT_ADDRESS = "SECRET_STORE_VAULT_ADDRESS";
  private static final String SECRET_STORE_VAULT_ENABLE_SSL = "SECRET_STORE_VAULT_ENABLE_SSL";
  private static final String SECRET_STORE_VAULT_PEM_FILE_PATH = "SECRET_STORE_VAULT_PEM_FILE_PATH";
  private static final String SECRET_STORE_VAULT_KEYSTORE_PASSWORD = "SECRET_STORE_VAULT_KEYSTORE_PASSWORD";
  private static final String SECRET_STORE_VAULT_KEYSTORE_FILE_PATH = "SECRET_STORE_VAULT_KEYSTORE_FILE_PATH";
  private static final String SECRET_STORE_VAULT_TRUSTSTORE_FILE_PATH = "SECRET_STORE_VAULT_TRUSTSTORE_FILE_PATH";

  private static final String SECRET_STORE_AWS_SSM_REGION = "SECRET_STORE_AWS_SSM_REGION";
  private static final String SECRET_STORE_AWS_SSM_USE_IAM = "SECRET_STORE_AWS_SSM_USE_IAM";
  private static final String SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_ENDPOINT = "SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_ENDPOINT";
  private static final String SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_PATH = "SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_PATH";

  private static final String SECRET_STORE_FSSP_ADDRESS = "SECRET_STORE_FSSP_ADDRESS";
  private static final String SECRET_STORE_FSSP_SECRET_PATH = "SECRET_STORE_FSSP_SECRET_PATH";
  private static final String SECRET_STORE_FSSP_ENABLE_SSL = "SECRET_STORE_FSSP_ENABLE_SSL";
  private static final String SECRET_STORE_FSSP_TRUSTSTORE_PATH = "SECRET_STORE_FSSP_TRUSTSTORE_PATH";
  private static final String SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE = "SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE";
  private static final String SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD = "SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD";

  @Bean
  public SecureStore secureStore() {
    return getSecureStoreByType();
  }

  protected SecureStore getSecureStoreByType() {
    var secureStoreType = getSecretStoreType();
    log.info("secureStore:: Using {} secure store type", secureStoreType);
    return switch (secureStoreType) {
      case EPHEMERAL -> createEphemeralStore(new HashMap<>());
      case AWS_SSM -> createAwsStore();
      case VAULT -> createVaultStore();
      case FSSP -> createFssStore();
    };
  }

  protected SecureStore createEphemeralStore(Map<String, String> ephemeralProperties) {
    return EphemeralStore.create(new EphemeralConfigProperties(ephemeralProperties));
  }

  protected SecureStore createAwsStore() {
    return AwsStore.create(AwsConfigProperties.builder()
      .region(getRequiredValue(SECRET_STORE_AWS_SSM_REGION))
      .useIam(getValue(SECRET_STORE_AWS_SSM_USE_IAM, TRUE))
      .ecsCredentialsEndpoint(getValue(SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_ENDPOINT))
      .ecsCredentialsPath(getValue(SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_PATH))
      .build());
  }

  protected SecureStore createVaultStore() {
    return VaultStore.create(VaultConfigProperties.builder()
      .token(getRequiredValue(SECRET_STORE_VAULT_TOKEN))
      .address(getRequiredValue(SECRET_STORE_VAULT_ADDRESS))
      .enableSsl(getValue(SECRET_STORE_VAULT_ENABLE_SSL, FALSE))
      .pemFilePath(getValue(SECRET_STORE_VAULT_PEM_FILE_PATH))
      .keystorePassword(getValue(SECRET_STORE_VAULT_KEYSTORE_PASSWORD))
      .keystoreFilePath(getValue(SECRET_STORE_VAULT_KEYSTORE_FILE_PATH))
      .truststoreFilePath(getValue(SECRET_STORE_VAULT_TRUSTSTORE_FILE_PATH))
      .secretRoot(DEFAULT_VAULT_SECRET_ROOT)
      .build());
  }

  protected SecureStore createFssStore() {
    return new FsspStore(FsspConfigProperties.builder()
      .address(getRequiredValue(SECRET_STORE_FSSP_ADDRESS))
      .secretPath(getValue(SECRET_STORE_FSSP_SECRET_PATH))
      .enableSsl(getValue(SECRET_STORE_FSSP_ENABLE_SSL, FALSE))
      .trustStorePath(getValue(SECRET_STORE_FSSP_TRUSTSTORE_PATH))
      .trustStoreFileType(getValue(SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE))
      .trustStorePassword(getValue(SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD))
      .build());
  }

  public static String getEnvId() {
    return Optional.ofNullable(System.getenv().get(ENV)).orElse(DEFAULT_ENV_ID);
  }

  public static SecureStoreType getSecretStoreType() {
    return Optional.ofNullable(System.getenv().get(SECRET_STORE_TYPE))
      .map(SecureStoreType::valueOf).orElse(EPHEMERAL);
  }

  private String getRequiredValue(String key) {
    return Optional.ofNullable(System.getenv().get(key))
      .orElseThrow(() -> new NoSuchElementException(NOT_FOUND_MESSAGE.formatted(key)));
  }

  private static String getValue(String key) {
    return Optional.ofNullable(System.getenv().get(key)).orElse("");
  }

  private static Boolean getValue(String key, boolean defaultValue) {
    return Optional.ofNullable(System.getenv().get(key))
      .map(Boolean::parseBoolean).orElse(defaultValue);
  }
}
