package org.folio.service.fiscalyear;

import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;

import io.vertx.core.Future;
import java.util.Optional;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.ws.rs.core.Response;
import org.folio.dao.fiscalyear.FiscalYearDAO;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.FiscalYearHierarchy;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBConn;

public class FiscalYearService {

  public static final int DEFAULT_FACTOR = 2;

  private final FiscalYearDAO fyDAO;

  public FiscalYearService(FiscalYearDAO fyDAO) {
    this.fyDAO = fyDAO;
  }

  public Future<FiscalYear> getFiscalYearById(String id, DBConn conn) {
    return fyDAO.getFiscalYearById(id, conn);
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

  public Future<FiscalYearHierarchy> getFiscalYearHierarchy(String fiscalYearId, DBConn conn) {
    return conn.getById(FISCAL_YEAR_TABLE, fiscalYearId, FiscalYear.class)
      .compose(fy -> {
        if (fy == null) {
          return Future.failedFuture(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "Fiscal year not found"));
        }
        return fyDAO.getFiscalYearHierarchyRows(fiscalYearId, conn)
          .map(rows -> FiscalYearHierarchyBuilder.build(fiscalYearId, rows));
      });
  }

}
