package org.folio.dao.exchangerate;

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.BooleanUtils;
import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ExchangeRateSourceDAOImpl implements ExchangeRateSourceDAO {

  private static final String EXCHANGE_RATE_SOURCE_TABLE = "exchange_rate_source";
  private static final String SELECT_EXCHANGE_RATE_SOURCE_QUERY = "SELECT jsonb FROM exchange_rate_source";
  private static final String EXISTS_EXCHANGE_RATE_SOURCE_QUERY = "SELECT CASE WHEN COUNT(e) > 0 THEN TRUE ELSE FALSE END FROM exchange_rate_source e WHERE e.id = $1";

  @Override
  public Future<Optional<ExchangeRateSource>> getExchangeRateSource(DBConn conn) {
    return conn.execute(SELECT_EXCHANGE_RATE_SOURCE_QUERY)
      .map(result -> result.size() == 0
        ? Optional.empty()
        : Optional.of(result.iterator().next().get(JsonObject.class, 0).mapTo(ExchangeRateSource.class)));
  }

  @Override
  public Future<ExchangeRateSource> saveExchangeRateSource(ExchangeRateSource exchangeRateSource, DBConn conn) {
    return getExchangeRateSource(conn)
      .compose(existingExchangeRateSource -> {
        if (existingExchangeRateSource.isPresent()) {
          return Future.failedFuture(new IllegalStateException(CONFLICT.getReasonPhrase()));
        }
        exchangeRateSource.setId(defaultIfBlank(exchangeRateSource.getId(), UUID.randomUUID().toString()));
        return conn.saveAndReturnUpdatedEntity(EXCHANGE_RATE_SOURCE_TABLE, exchangeRateSource.getId(), exchangeRateSource);
      });
  }

  @Override
  public Future<Void> updateExchangeRateSource(String id, ExchangeRateSource exchangeRateSource, DBConn conn) {
    return validateExchangeRateSourceExistsById(id, conn)
      .compose(exists -> conn.update(EXCHANGE_RATE_SOURCE_TABLE, exchangeRateSource, id))
      .mapEmpty();
  }

  @Override
  public Future<Void> deleteExchangeRateSource(String id, DBConn conn) {
    return validateExchangeRateSourceExistsById(id, conn)
      .compose(v -> conn.delete(EXCHANGE_RATE_SOURCE_TABLE, id))
      .mapEmpty();
  }

  private Future<Void> validateExchangeRateSourceExistsById(String id, DBConn conn) {
    return conn.execute(EXISTS_EXCHANGE_RATE_SOURCE_QUERY, Tuple.of(id))
      .map(result -> result.iterator().next().getBoolean(0))
      .compose(exists -> BooleanUtils.isTrue(exists)
        ? Future.succeededFuture()
        : Future.failedFuture(new IllegalStateException(NOT_FOUND.getReasonPhrase())));
  }

}
