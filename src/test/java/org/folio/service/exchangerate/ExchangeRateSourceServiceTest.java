package org.folio.service.exchangerate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.config.SecureStoreConfiguration;
import org.folio.config.SecureStoreType;
import org.folio.dao.exchangerate.ExchangeRateSourceDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.persist.DBClient;
import org.folio.tools.store.SecureStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

@SuppressWarnings("unchecked")
@CopilotGenerated(partiallyGenerated = true, model = "GPT-4.1")
public class ExchangeRateSourceServiceTest {

  private static final ExchangeRateSource EXCHANGE_RATE_SOURCE_VALID_TREASURY = new ExchangeRateSource()
    .withProviderType(ExchangeRateSource.ProviderType.TREASURY_GOV)
    .withProviderUri("providerURI");

  private static final ExchangeRateSource EXCHANGE_RATE_SOURCE_VALID_CONVERA = new ExchangeRateSource()
    .withProviderType(ExchangeRateSource.ProviderType.CONVERA_COM)
    .withProviderUri("providerURI")
    .withApiKey("apiKey")
    .withApiSecret("apiSecret");

  private static final ExchangeRateSource EXCHANGE_RATE_SOURCE_INVALID_CURRENCY_API = new ExchangeRateSource()
    .withProviderType(ExchangeRateSource.ProviderType.CURRENCYAPI_COM)
    .withProviderUri("providerURI")
    .withApiKey(null);

  private static final ExchangeRateSource EXCHANGE_RATE_SOURCE_INVALID_CONVERA = new ExchangeRateSource()
    .withProviderType(ExchangeRateSource.ProviderType.CONVERA_COM)
    .withProviderUri("providerURI")
    .withApiKey(null)
    .withApiSecret("apiSecret");

  private ExchangeRateSourceService exchangeRateSourceService;
  private RequestContext requestContext;
  private DBClient dbClient;
  private SecureStore secureStore;

  @BeforeEach
  void setUp() {
    secureStore = mock(SecureStore.class);
    exchangeRateSourceService = new ExchangeRateSourceService(secureStore, mock(ExchangeRateSourceDAO.class));
    requestContext = mock(RequestContext.class);
    dbClient = mock(DBClient.class);

    when(requestContext.toDBClient()).thenReturn(dbClient);
    when(requestContext.getHeaders()).thenReturn(java.util.Collections.emptyMap());
  }

  @Test
  void getExchangeRateSource_returnsExchangeRateSource_whenExists() {
    when(dbClient.withConn(any())).thenReturn(Future.succeededFuture(Optional.of(EXCHANGE_RATE_SOURCE_VALID_TREASURY)));

    var result = exchangeRateSourceService.getExchangeRateSource(requestContext);

    assertEquals(EXCHANGE_RATE_SOURCE_VALID_TREASURY, result.result());
  }

  @Test
  void getExchangeRateSource_throwsNotFound_whenNotExists() {
    when(dbClient.withConn(any())).thenReturn(Future.succeededFuture(Optional.empty()));

    var result = exchangeRateSourceService.getExchangeRateSource(requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(404, ((HttpException) result.cause()).getCode());
  }

  @Test
  void saveExchangeRateSource_savesSuccessfully_whenValid() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture(EXCHANGE_RATE_SOURCE_VALID_TREASURY));

    var result = exchangeRateSourceService.saveExchangeRateSource(EXCHANGE_RATE_SOURCE_VALID_TREASURY, requestContext);

    assertTrue(result.succeeded());
    assertEquals(EXCHANGE_RATE_SOURCE_VALID_TREASURY, result.result());
  }

  @Test
  void saveExchangeRateSource_fails_whenInvalid() {
    var exchangeRateSource = new ExchangeRateSource();

    var result = exchangeRateSourceService.saveExchangeRateSource(exchangeRateSource, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(422, ((HttpException) result.cause()).getCode());
  }

  @Test
  void saveExchangeRateSource_fails_whenInvalid_CurrencyApi() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture(EXCHANGE_RATE_SOURCE_INVALID_CURRENCY_API));

    var result = exchangeRateSourceService.saveExchangeRateSource(EXCHANGE_RATE_SOURCE_INVALID_CURRENCY_API, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(422, ((HttpException) result.cause()).getCode());
  }

  @Test
  void saveExchangeRateSource_fails_whenInvalid_Convera() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture(EXCHANGE_RATE_SOURCE_INVALID_CONVERA));

    var result = exchangeRateSourceService.saveExchangeRateSource(EXCHANGE_RATE_SOURCE_INVALID_CONVERA, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(422, ((HttpException) result.cause()).getCode());
  }

  @Test
  void updateExchangeRateSource_updatesSuccessfully_whenExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture());

    var result = exchangeRateSourceService.updateExchangeRateSource(UUID.randomUUID().toString(), EXCHANGE_RATE_SOURCE_VALID_TREASURY, requestContext);

    assertTrue(result.succeeded());
  }

  @Test
  void updateExchangeRateSource_fails_whenNotExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.failedFuture(new HttpException(404, "Not Found")));

    var result = exchangeRateSourceService.updateExchangeRateSource(UUID.randomUUID().toString(), EXCHANGE_RATE_SOURCE_VALID_TREASURY, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(404, ((HttpException) result.cause()).getCode());
  }

  @Test
  void deleteExchangeRateSource_deletesSuccessfully_whenExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture());

    var result = exchangeRateSourceService.deleteExchangeRateSource(UUID.randomUUID().toString(), requestContext);

    assertTrue(result.succeeded());
  }

  @Test
  void deleteExchangeRateSource_fails_whenNotExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.failedFuture(new HttpException(404, "Not Found")));

    var result = exchangeRateSourceService.deleteExchangeRateSource(UUID.randomUUID().toString(), requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(404, ((HttpException) result.cause()).getCode());
  }

  @Test
  void getExchangeRateSource_usesSecureStoreLookup_whenEnabled() {
    try (var mocked = mockStatic(SecureStoreConfiguration.class)) {
      mocked.when(SecureStoreConfiguration::getSecretStoreType).thenReturn(SecureStoreType.VAULT);

      when(dbClient.withConn(any())).thenReturn(Future.succeededFuture(Optional.of(EXCHANGE_RATE_SOURCE_VALID_TREASURY)));
      when(secureStore.lookup(any())).thenReturn(Optional.of("apiKey")).thenReturn(Optional.of("apiSecret"));

      var result = exchangeRateSourceService.getExchangeRateSource(requestContext);

      assertTrue(result.succeeded());
      verify(secureStore, times(1)).lookup(contains("exchange-rate-api-key"));
      verify(secureStore, times(1)).lookup(contains("exchange-rate-api-secret"));
    }
  }

  @Test
  void saveExchangeRateSource_usesSecureStoreSetAndDelete_whenEnabled() {
    try (var mocked = mockStatic(SecureStoreConfiguration.class)) {
      mocked.when(SecureStoreConfiguration::getSecretStoreType).thenReturn(SecureStoreType.VAULT);

      var dbConn = mock(org.folio.rest.persist.DBConn.class);
      when(dbClient.withTrans(any())).then(invocation -> {
        invocation.getArgument(0, java.util.function.Function.class).apply(dbConn);
        return Future.succeededFuture(EXCHANGE_RATE_SOURCE_VALID_CONVERA);
      });

      var result = exchangeRateSourceService.saveExchangeRateSource(EXCHANGE_RATE_SOURCE_VALID_CONVERA, requestContext);

      assertTrue(result.succeeded());
      verify(secureStore, times(1)).set(contains("exchange-rate-api-key"), eq("apiKey"));
      verify(secureStore, times(1)).set(contains("exchange-rate-api-secret"), eq("apiSecret"));
    }
  }

  @Test
  void saveExchangeRateSource_usesSecureStoreDelete_whenApiKeyOrSecretNull() {
    try (var mocked = mockStatic(SecureStoreConfiguration.class)) {
      mocked.when(SecureStoreConfiguration::getSecretStoreType).thenReturn(SecureStoreType.VAULT);

      var source = new ExchangeRateSource()
        .withProviderType(ExchangeRateSource.ProviderType.CONVERA_COM)
        .withProviderUri("providerURI")
        .withApiKey(null)
        .withApiSecret(null);

      var dbConn = mock(org.folio.rest.persist.DBConn.class);
      when(dbClient.withTrans(any())).then(invocation -> {
        invocation.getArgument(0, java.util.function.Function.class).apply(dbConn);
        return Future.succeededFuture(source);
      });

      var result = exchangeRateSourceService.saveExchangeRateSource(source, requestContext);

      assertTrue(result.failed());
      assertInstanceOf(HttpException.class, result.cause());
      assertEquals(422, ((HttpException) result.cause()).getCode());
    }
  }

  @Test
  void deleteExchangeRateSource_usesSecureStoreDelete_whenEnabled() {
    try (var mocked = mockStatic(SecureStoreConfiguration.class)) {
      mocked.when(SecureStoreConfiguration::getSecretStoreType).thenReturn(SecureStoreType.VAULT);

      var dbConn = mock(org.folio.rest.persist.DBConn.class);
      when(dbClient.withTrans(any())).then(invocation -> {
        invocation.getArgument(0, java.util.function.Function.class).apply(dbConn);
        return Future.succeededFuture();
      });

      var result = exchangeRateSourceService.deleteExchangeRateSource(UUID.randomUUID().toString(), requestContext);

      assertTrue(result.succeeded());
      verify(secureStore, times(1)).delete(contains("exchange-rate-api-key"));
      verify(secureStore, times(1)).delete(contains("exchange-rate-api-secret"));
    }
  }
}
