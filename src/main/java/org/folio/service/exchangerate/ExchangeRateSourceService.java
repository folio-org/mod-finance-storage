package org.folio.service.exchangerate;

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.utils.SecureStoreUtils.buildKey;
import static org.folio.utils.SecureStoreUtils.isSecureStoreEnabled;

import org.apache.commons.lang3.StringUtils;
import org.folio.dao.exchangerate.ExchangeRateSourceDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.util.ErrorCodes;

import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.tools.store.SecureStore;

@Log4j2
@AllArgsConstructor
public class ExchangeRateSourceService {

  private static final String EXCHANGE_RATE_API_KEY = "exchange-rate-api-key";
  private static final String EXCHANGE_RATE_API_SECRET = "exchange-rate-api-secret";

  private SecureStore secureStore;
  private ExchangeRateSourceDAO exchangeRateSourceDAO;

  public Future<ExchangeRateSource> getExchangeRateSource(RequestContext requestContext) {
    var dbClient = requestContext.toDBClient();
    return dbClient.withConn(conn -> exchangeRateSourceDAO.getExchangeRateSource(conn))
      .map(exchangeRateSource -> exchangeRateSource.map(source -> {
        if (isSecureStoreEnabled()) {
          secureStore.lookup(buildKey(EXCHANGE_RATE_API_KEY, requestContext)).ifPresent(source::withApiKey);
          secureStore.lookup(buildKey(EXCHANGE_RATE_API_SECRET, requestContext)).ifPresent(source::withApiSecret);
        }
        return source;
      }).orElseThrow(() ->
        new HttpException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase())));
  }

  public Future<ExchangeRateSource> saveExchangeRateSource(ExchangeRateSource exchangeRateSource, RequestContext requestContext) {
    if (validateExchangeRateSource(exchangeRateSource)) {
      return Future.failedFuture(new HttpException(422, ErrorCodes.EXCHANGE_RATE_SOURCE_INVALID.toError()));
    }
    return requestContext.toDBClient()
      .withTrans(conn -> {
        if (isSecureStoreEnabled()) {
          updateSecureStoreSource(exchangeRateSource, requestContext);
        }
        return exchangeRateSourceDAO.saveExchangeRateSource(exchangeRateSource, conn);
      })
      .recover(this::handleException)
      .onSuccess(v -> log.info("saveExchangeRateSource:: New exchange rate source was saved successfully"))
      .onFailure(t -> log.error("Failed to save exchange rate source", t));
  }

  public Future<Void> updateExchangeRateSource(String id, ExchangeRateSource exchangeRateSource, RequestContext requestContext) {
    if (validateExchangeRateSource(exchangeRateSource)) {
      return Future.failedFuture(new HttpException(422, ErrorCodes.EXCHANGE_RATE_SOURCE_INVALID.toError()));
    }
    return requestContext.toDBClient()
      .withTrans(conn -> {
        if (isSecureStoreEnabled()) {
          updateSecureStoreSource(exchangeRateSource, requestContext);
        }
        return exchangeRateSourceDAO.updateExchangeRateSource(id, exchangeRateSource, conn);
      })
      .recover(this::handleException)
      .onSuccess(v -> log.info("updateExchangeRateSource:: Exchange rate source with id: '{}' was updated successfully", id))
      .onFailure(t -> log.error("Failed to update exchange rate source with id: '{}'", id, t));
  }

  public Future<Void> deleteExchangeRateSource(String id, RequestContext requestContext) {
    var dbClient = requestContext.toDBClient();
    return dbClient.withTrans(conn -> {
        if (isSecureStoreEnabled()) {
          secureStore.delete(buildKey(EXCHANGE_RATE_API_KEY, requestContext));
          secureStore.delete(buildKey(EXCHANGE_RATE_API_SECRET, requestContext));
        }
        return exchangeRateSourceDAO.deleteExchangeRateSource(id, conn);
      })
      .recover(this::handleException)
      .onSuccess(v -> log.info("deleteExchangeRateSource:: Exchange rate source with id: '{}' was deleted successfully", id))
      .onFailure(t -> log.error("Failed to delete exchange rate source with id: '{}'", id, t));
  }

  private boolean validateExchangeRateSource(ExchangeRateSource exchangeRateSource) {
    return exchangeRateSource == null
      || !StringUtils.isNotEmpty(exchangeRateSource.getProviderUri())
      || !isValidApiCredentials(exchangeRateSource);
  }

  private boolean isValidApiCredentials(ExchangeRateSource exchangeRateSource) {
    return switch (exchangeRateSource.getProviderType()) {
      case TREASURY_GOV -> true;
      case CURRENCYAPI_COM -> StringUtils.isNotEmpty(exchangeRateSource.getApiKey());
      default -> StringUtils.isNotEmpty(exchangeRateSource.getApiKey())
        && StringUtils.isNotEmpty(exchangeRateSource.getApiSecret());
    };
  }

  private <T> Future<T> handleException(Throwable throwable) {
    if (throwable instanceof HttpException) {
      return Future.failedFuture(throwable);
    } else if (!(throwable instanceof IllegalStateException)) {
      throwable = new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(), throwable);
    } else if (NOT_FOUND.getReasonPhrase().equals(throwable.getMessage())) {
      throwable = new HttpException(NOT_FOUND.getStatusCode(), throwable);
    } else if (CONFLICT.getReasonPhrase().equals(throwable.getMessage())) {
      throwable = new HttpException(CONFLICT.getStatusCode(), ErrorCodes.EXCHANGE_RATE_SOURCE_ALREADY_EXISTS.toError());
    }

    return Future.failedFuture(throwable);
  }

  private void updateSecureStoreSource(ExchangeRateSource exchangeRateSource, RequestContext requestContext) {
    var exchangeRateApiKey = buildKey(EXCHANGE_RATE_API_KEY, requestContext);
    if (StringUtils.isNotEmpty(exchangeRateSource.getApiKey())) {
      secureStore.set(exchangeRateApiKey, exchangeRateSource.getApiKey());
    } else {
      secureStore.delete(exchangeRateApiKey);
    }
    var exchangeRateApiSecret = buildKey(EXCHANGE_RATE_API_SECRET, requestContext);
    if (StringUtils.isNotEmpty(exchangeRateSource.getApiSecret())) {
      secureStore.set(exchangeRateApiSecret, exchangeRateSource.getApiSecret());
    } else {
      secureStore.delete(exchangeRateApiSecret);
    }
    exchangeRateSource.withApiKey(null);
    exchangeRateSource.withApiSecret(null);
  }
}
