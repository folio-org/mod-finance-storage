package org.folio.service.transactions.batch;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;

import javax.money.CurrencyUnit;

import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.subtractMoneyOrDefault;
import static org.folio.utils.MoneyUtils.sumMoney;

@Log4j2
@UtilityClass
public class EncumbranceUtil {

  public static void updateNewAmountWithUnappliedCreditOnApproval(Transaction encumbrance, CurrencyUnit currency,
                                                                  double awaitingPayment, double newAmount) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    if (initialAmount > 0 && awaitingPayment < 0) {
      double newAmountWithUnappliedCredited = sumMoney(newAmount, Math.abs(awaitingPayment), currency);
      if (newAmountWithUnappliedCredited <= initialAmount) {
        encumbrance.setAmount(newAmountWithUnappliedCredited);
      }
    } else {
      encumbrance.setAmount(newAmount);
    }
  }

  public static void updateNewAmountWithUnappliedCreditOnPayment(Transaction encumbrance, CurrencyUnit currency,
                                                                 double amount, double newAwaitingPayment, double newExpended, double newCredited) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    if (initialAmount > 0 && newCredited > 0) {
      double newAmountWithUnappliedCredited = sumMoney(encumbrance.getAmount(), newCredited, currency);
      if (sumMoney(currency, amount, newAwaitingPayment, newExpended) < initialAmount) {
        encumbrance.setAmount(newAmountWithUnappliedCredited);
      }
    }
  }

  public static double calculateNewAmountDefaultOnZero(Transaction encumbrance, CurrencyUnit currency,
                                                       double awaitingPayment, double expended, double credited) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    double newAmount = subtractMoneyOrDefault(initialAmount, awaitingPayment, 0d, currency);
    newAmount = subtractMoneyOrDefault(newAmount, expended, 0d, currency);
    newAmount = sumMoney(newAmount, credited, currency);
    log.info("calculateNewAmountDefaultOnZero:: Encumbrance initialAmount={} oldAmount={} awaitingPayment={} expended={} credited={} newAmount={}",
      initialAmount, encumbrance.getAmount(), awaitingPayment, expended, credited, newAmount);
    return newAmount;
  }

  public static double calculateNewAmount(Transaction encumbrance, CurrencyUnit currency,
                                          double awaitingPayment, double expended, double credited) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    double newAmount = subtractMoney(initialAmount, awaitingPayment, currency);
    newAmount = subtractMoney(newAmount, expended, currency);
    newAmount = sumMoney(newAmount, credited, currency);
    log.info("calculateNewAmount:: Encumbrance initialAmount={} oldAmount={} awaitingPayment={} expended={} credited={} newAmount={}",
      initialAmount, encumbrance.getAmount(), awaitingPayment, expended, credited, newAmount);
    return newAmount;
  }

  public static double defaultNewAmountOnUnrelease(Transaction encumbrance, double newAmount, double defaultAmount, Encumbrance.Status fromStatus) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    if (newAmount > initialAmount) {
      log.warn("defaultNewAmountOnUnrelease:: Transition from {} to UNRELEASED with newAmount={} " +
        "exceeding initialAmount and will be defaulted at initialAmount={}", fromStatus.name(), newAmount, initialAmount);
      newAmount = initialAmount;
    } else if (newAmount < 0) {
      log.warn("capNewAmountOnUnrelease:: Transition from {} to UNRELEASED with newAmount={} " +
        "going below 0 and will be defaulted at defaultAmount={}", fromStatus.name(), newAmount, defaultAmount);
      newAmount = defaultAmount;
    }
    return newAmount;
  }
}
