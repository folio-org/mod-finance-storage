package org.folio.service.fiscalyear;

import io.vertx.core.Future;
import java.util.Optional;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import org.folio.dao.fiscalyear.FiscalYearDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;

public class FiscalYearService {

  public static final int DEFAULT_FACTOR = 2;

  private final FiscalYearDAO fyDAO;

  public FiscalYearService(FiscalYearDAO fyDAO) {
    this.fyDAO = fyDAO;
  }

  private Future<Integer> getCurrencyFactorNumber(String fiscalYearId, RequestContext requestContext) {
    DBClient dbClient = new DBClient(requestContext);
    return fyDAO.getFiscalYearById(fiscalYearId, dbClient)
      .map(this::getFiscalYearCurrencyFactor);
  }

  private Integer getFiscalYearCurrencyFactor(FiscalYear fiscalYear) {
    return Optional.ofNullable(fiscalYear)
      .map(fy -> Monetary.getCurrency(fy.getCurrency()))
      .map(CurrencyUnit::getDefaultFractionDigits)
      .orElse(DEFAULT_FACTOR);
  }

  public Future<Void> populateRolloverWithCurrencyFactor(LedgerFiscalYearRollover rollover, RequestContext requestContext) {
    return getCurrencyFactorNumber(rollover.getFromFiscalYearId(), requestContext)
      .map(factor -> {
        rollover.setCurrencyFactor(factor);
        return null;
      });
  }

}
