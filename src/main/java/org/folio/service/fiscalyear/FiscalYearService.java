package org.folio.service.fiscalyear;

import io.vertx.core.Future;
import java.util.Optional;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import org.folio.dao.fiscalyear.FiscalYearDAO;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBConn;

public class FiscalYearService {

  public static final int DEFAULT_FACTOR = 2;

  private final FiscalYearDAO fyDAO;

  public FiscalYearService(FiscalYearDAO fyDAO) {
    this.fyDAO = fyDAO;
  }

  private Future<Integer> getCurrencyFactorNumber(String fiscalYearId, DBConn conn) {
    return fyDAO.getFiscalYearById(fiscalYearId, conn)
      .map(this::getFiscalYearCurrencyFactor);
  }

  private Integer getFiscalYearCurrencyFactor(FiscalYear fiscalYear) {
    return Optional.ofNullable(fiscalYear)
      .map(fy -> Monetary.getCurrency(fy.getCurrency()))
      .map(CurrencyUnit::getDefaultFractionDigits)
      .orElse(DEFAULT_FACTOR);
  }

  public Future<Void> populateRolloverWithCurrencyFactor(LedgerFiscalYearRollover rollover, DBConn conn) {
    return getCurrencyFactorNumber(rollover.getFromFiscalYearId(), conn)
      .map(factor -> {
        rollover.setCurrencyFactor(factor);
        return null;
      });
  }

}
