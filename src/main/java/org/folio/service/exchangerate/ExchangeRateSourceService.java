package org.folio.service.exchangerate;

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.folio.dao.exchangerate.ExchangeRateSourceDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.util.ErrorCodes;

import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@AllArgsConstructor
public class ExchangeRateSourceService {

  private ExchangeRateSourceDAO exchangeRateSourceDAO;

  public Future<ExchangeRateSource> getExchangeRateSource(RequestContext requestContext) {
    var dbClient = requestContext.toDBClient();
    return dbClient.withConn(conn -> exchangeRateSourceDAO.getExchangeRateSource(conn))
      .map(exchangeRateSource -> exchangeRateSource.orElseThrow(() ->
        new HttpException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase())));
  }

  public Future<ExchangeRateSource> saveExchangeRateSource(ExchangeRateSource exchangeRateSource, RequestContext requestContext) {
    removeSensitiveData(exchangeRateSource);
    if (!validateExchangeRateSource(exchangeRateSource)) {
      return Future.failedFuture(new HttpException(422, ErrorCodes.EXCHANGE_RATE_SOURCE_INVALID.toError()));
    }
    return requestContext.toDBClient()
      .withTrans(conn -> exchangeRateSourceDAO.saveExchangeRateSource(exchangeRateSource, conn))
      .recover(this::handleException)
      .onSuccess(v -> log.info("saveExchangeRateSource:: New exchange rate source was saved successfully"))
      .onFailure(t -> log.error("Failed to save exchange rate source", t));
  }

  public Future<Void> updateExchangeRateSource(String id, ExchangeRateSource exchangeRateSource, RequestContext requestContext) {
    removeSensitiveData(exchangeRateSource);
    if (!validateExchangeRateSource(exchangeRateSource)) {
      return Future.failedFuture(new HttpException(422, ErrorCodes.EXCHANGE_RATE_SOURCE_INVALID.toError()));
    }
    return requestContext.toDBClient()
      .withTrans(conn -> exchangeRateSourceDAO.updateExchangeRateSource(id, exchangeRateSource, conn))
      .recover(this::handleException)
      .onSuccess(v -> log.info("updateExchangeRateSource:: Exchange rate source with id: '{}' was updated successfully", id))
      .onFailure(t -> log.error("Failed to update exchange rate source with id: '{}'", id, t));
  }

  public Future<Void> deleteExchangeRateSource(String id, RequestContext requestContext) {
    var dbClient = requestContext.toDBClient();
    return dbClient.withTrans(conn -> exchangeRateSourceDAO.deleteExchangeRateSource(id, conn))
      .recover(this::handleException)
      .onSuccess(v -> log.info("deleteExchangeRateSource:: Exchange rate source with id: '{}' was deleted successfully", id))
      .onFailure(t -> log.error("Failed to delete exchange rate source with id: '{}'", id, t));
  }

  private void removeSensitiveData(ExchangeRateSource exchangeRateSource) {
    exchangeRateSource.setApiKey(null);
    exchangeRateSource.setApiSecret(null);
  }

  private boolean validateExchangeRateSource(ExchangeRateSource exchangeRateSource) {
    return exchangeRateSource != null
      && StringUtils.isNotEmpty(exchangeRateSource.getProviderUri())
      && StringUtils.isEmpty(exchangeRateSource.getApiKey())
      && StringUtils.isEmpty(exchangeRateSource.getApiSecret())
      && Objects.nonNull(exchangeRateSource.getRefreshInterval())
      && exchangeRateSource.getRefreshInterval() > 0;
  }

  private <T> Future<T> handleException(Throwable t) {
    if (t instanceof HttpException) {
      return Future.failedFuture(t);
    } else if (!(t instanceof IllegalStateException)) {
      t = new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(), t);
    } else if (NOT_FOUND.getReasonPhrase().equals(t.getMessage())) {
      t = new HttpException(NOT_FOUND.getStatusCode(), t);
    } else if (CONFLICT.getReasonPhrase().equals(t.getMessage())) {
      t = new HttpException(CONFLICT.getStatusCode(), ErrorCodes.EXCHANGE_RATE_SOURCE_ALREADY_EXISTS.toError());
    }
    return Future.failedFuture(t);
  }

}
