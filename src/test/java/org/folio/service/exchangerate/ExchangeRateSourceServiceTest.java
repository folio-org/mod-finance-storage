package org.folio.service.exchangerate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.config.SecureStoreConfiguration;
import org.folio.dao.exchangerate.ExchangeRateSourceDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.persist.DBClient;
import org.folio.tools.store.SecureStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

@CopilotGenerated(partiallyGenerated = true)
public class ExchangeRateSourceServiceTest {

  private static final ExchangeRateSource EXCHANGE_RATE_SOURCE_VALID_TREASURY = new ExchangeRateSource()
    .withProviderType(ExchangeRateSource.ProviderType.TREASURY_GOV).withProviderUri("providerURI");
  private static final ExchangeRateSource EXCHANGE_RATE_SOURCE_INVALID_CURRENCY_API = new ExchangeRateSource()
    .withProviderType(ExchangeRateSource.ProviderType.CURRENCYAPI_COM).withProviderUri("providerURI")
    .withApiKey(null);
  private static final ExchangeRateSource EXCHANGE_RATE_SOURCE_INVALID_CONVERA = new ExchangeRateSource()
    .withProviderType(ExchangeRateSource.ProviderType.CONVERA_COM).withProviderUri("providerURI")
    .withApiKey(null).withApiSecret("apiSecret");

  private ExchangeRateSourceService exchangeRateSourceService;
  private RequestContext requestContext;
  private DBClient dbClient;

  @BeforeEach
  void setUp() {
    exchangeRateSourceService = new ExchangeRateSourceService(mock(SecureStore.class), mock(SecureStoreConfiguration.class), mock(ExchangeRateSourceDAO.class));
    requestContext = mock(RequestContext.class);
    dbClient = mock(DBClient.class);
    when(requestContext.toDBClient()).thenReturn(dbClient);
  }

  @Test
  void getExchangeRateSource_returnsExchangeRateSource_whenExists() {
    when(dbClient.withConn(any())).thenReturn(Future.succeededFuture(Optional.of(EXCHANGE_RATE_SOURCE_VALID_TREASURY)));

    Future<ExchangeRateSource> result = exchangeRateSourceService.getExchangeRateSource(requestContext);

    assertEquals(EXCHANGE_RATE_SOURCE_VALID_TREASURY, result.result());
  }

  @Test
  void getExchangeRateSource_throwsNotFound_whenNotExists() {
    when(dbClient.withConn(any())).thenReturn(Future.succeededFuture(Optional.empty()));

    Future<ExchangeRateSource> result = exchangeRateSourceService.getExchangeRateSource(requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(404, ((HttpException) result.cause()).getCode());
  }

  @Test
  void saveExchangeRateSource_savesSuccessfully_whenValid() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture(EXCHANGE_RATE_SOURCE_VALID_TREASURY));

    Future<ExchangeRateSource> result = exchangeRateSourceService.saveExchangeRateSource(EXCHANGE_RATE_SOURCE_VALID_TREASURY, requestContext);

    assertTrue(result.succeeded());
    assertEquals(EXCHANGE_RATE_SOURCE_VALID_TREASURY, result.result());
  }

  @Test
  void saveExchangeRateSource_fails_whenInvalid() {
    var exchangeRateSource = new ExchangeRateSource();

    Future<ExchangeRateSource> result = exchangeRateSourceService.saveExchangeRateSource(exchangeRateSource, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(422, ((HttpException) result.cause()).getCode());
  }

  @Test
  void saveExchangeRateSource_fails_whenInvalid_CurrencyApi() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture(EXCHANGE_RATE_SOURCE_INVALID_CURRENCY_API));

    Future<ExchangeRateSource> result = exchangeRateSourceService.saveExchangeRateSource(EXCHANGE_RATE_SOURCE_INVALID_CURRENCY_API, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(422, ((HttpException) result.cause()).getCode());
  }

  @Test
  void saveExchangeRateSource_fails_whenInvalid_Convera() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture(EXCHANGE_RATE_SOURCE_INVALID_CONVERA));

    Future<ExchangeRateSource> result = exchangeRateSourceService.saveExchangeRateSource(EXCHANGE_RATE_SOURCE_INVALID_CONVERA, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(422, ((HttpException) result.cause()).getCode());
  }

  @Test
  void updateExchangeRateSource_updatesSuccessfully_whenExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture());

    Future<Void> result = exchangeRateSourceService.updateExchangeRateSource(UUID.randomUUID().toString(), EXCHANGE_RATE_SOURCE_VALID_TREASURY, requestContext);

    assertTrue(result.succeeded());
  }

  @Test
  void updateExchangeRateSource_fails_whenNotExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.failedFuture(new HttpException(404, "Not Found")));

    Future<Void> result = exchangeRateSourceService.updateExchangeRateSource(UUID.randomUUID().toString(), EXCHANGE_RATE_SOURCE_VALID_TREASURY, requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(404, ((HttpException) result.cause()).getCode());
  }

  @Test
  void deleteExchangeRateSource_deletesSuccessfully_whenExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture());

    Future<Void> result = exchangeRateSourceService.deleteExchangeRateSource(UUID.randomUUID().toString(), requestContext);

    assertTrue(result.succeeded());
  }

  @Test
  void deleteExchangeRateSource_fails_whenNotExists() {
    when(dbClient.withTrans(any())).thenReturn(Future.failedFuture(new HttpException(404, "Not Found")));

    Future<Void> result = exchangeRateSourceService.deleteExchangeRateSource(UUID.randomUUID().toString(), requestContext);

    assertTrue(result.failed());
    assertInstanceOf(HttpException.class, result.cause());
    assertEquals(404, ((HttpException) result.cause()).getCode());
  }

}
