package org.folio.dao.exchangerate;

import java.util.Optional;

import org.folio.rest.jaxrs.model.ExchangeRateSource;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Future;

public interface ExchangeRateSourceDAO {

  /**
   * Get exchange rate source. Only one exchange rate source can exist at a time.
   * If no exchange rate source is found, return empty optional.
   *
   * @param conn DB connection
   * @return exchange rate source optional
   */
  Future<Optional<ExchangeRateSource>> getExchangeRateSource(DBConn conn);


  /**
   * Save exchange rate source. If exchange rate source already exists, validation error should be returned.
   *
   * @param exchangeRateSource exchange rate source to save
   * @param conn DB connection
   */
  Future<Void> saveExchangeRateSource(ExchangeRateSource exchangeRateSource, DBConn conn);


  /**
   * Update an existing exchange rate source. If exchange rate source does not exist, validation error should be returned.
   *
   * @param id exchange rate source id
   * @param exchangeRateSource exchange rate source to update
   * @param conn DB connection
   */
  Future<Void> updateExchangeRateSource(String id, ExchangeRateSource exchangeRateSource, DBConn conn);


  /**
   * Delete exchange rate source. If exchange rate source does not exist, validation error should be returned.
   *
   * @param id exchange rate source id
   * @param conn DB connection
   */
  Future<Void> deleteExchangeRateSource(String id, DBConn conn);

}
