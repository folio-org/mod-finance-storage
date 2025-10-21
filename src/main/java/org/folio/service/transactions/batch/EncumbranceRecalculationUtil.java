package org.folio.service.transactions.batch;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.rest.jaxrs.model.Transaction;

import javax.money.CurrencyUnit;

import static org.folio.utils.MoneyUtils.subtractMoneyOrDefault;
import static org.folio.utils.MoneyUtils.sumMoney;

@Log4j2
@UtilityClass
public class EncumbranceRecalculationUtil {

  // Formula newAmount = initialAmount - awaitingPayment - expended + credited
  public static double recalculate(Transaction encumbrance, CurrencyUnit currency,
                                   double awaitingPayment, double expended, double credited) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    double oldAmount = encumbrance.getAmount();
    double newAmount = subtractMoneyOrDefault(initialAmount, awaitingPayment, 0d, currency);
    newAmount = subtractMoneyOrDefault(newAmount, expended, 0d, currency);
    newAmount = sumMoney(newAmount, credited, currency);
    log.info("recalculate:: Encumbrance initialAmount={} oldAmount={} awaitingPayment={} expended={} credited={} newAmount={}",
      initialAmount, oldAmount, awaitingPayment, expended, credited, newAmount);
    return newAmount;
  }
}
