package org.folio.service.exchangerate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.dao.exchangerate.ExchangeRateSourceDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

@CopilotGenerated(partiallyGenerated = true)
public class ExchangeRateSourceServiceTest {

  private ExchangeRateSourceService exchangeRateSourceService;
  private RequestContext requestContext;
  private DBClient dbClient;

  @BeforeEach
  void setUp() {
    exchangeRateSourceService = new ExchangeRateSourceService(mock(ExchangeRateSourceDAO.class));
    requestContext = mock(RequestContext.class);
    dbClient = mock(DBClient.class);
    when(requestContext.toDBClient()).thenReturn(dbClient);
  }

  @Test
  void getExchangeRateSource_returnsExchangeRateSource_whenExists() {
    var exchangeRateSource = new ExchangeRateSource();
    when(dbClient.withConn(any())).thenReturn(Future.succeededFuture(Optional.of(exchangeRateSource)));

    Future<ExchangeRateSource> result = exchangeRateSourceService.getExchangeRateSource(requestContext);

    assertEquals(exchangeRateSource, result.result());
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
    var exchangeRateSource = new ExchangeRateSource().withProviderUri("providerURI");
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture(exchangeRateSource));

    Future<ExchangeRateSource> result = exchangeRateSourceService.saveExchangeRateSource(exchangeRateSource, requestContext);

    assertTrue(result.succeeded());
    assertEquals(exchangeRateSource, result.result());
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
  void updateExchangeRateSource_updatesSuccessfully_whenExists() {
    var exchangeRateSource = new ExchangeRateSource().withProviderUri("providerURI");
    when(dbClient.withTrans(any())).thenReturn(Future.succeededFuture());

    Future<Void> result = exchangeRateSourceService.updateExchangeRateSource(UUID.randomUUID().toString(), exchangeRateSource, requestContext);

    assertTrue(result.succeeded());
  }

  @Test
  void updateExchangeRateSource_fails_whenNotExists() {
    var exchangeRateSource = new ExchangeRateSource().withProviderUri("providerURI");
    when(dbClient.withTrans(any())).thenReturn(Future.failedFuture(new HttpException(404, "Not Found")));

    Future<Void> result = exchangeRateSourceService.updateExchangeRateSource(UUID.randomUUID().toString(), exchangeRateSource, requestContext);

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
