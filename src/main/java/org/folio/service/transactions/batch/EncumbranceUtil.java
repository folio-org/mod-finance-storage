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

  // Approval directly affects both "awaiting payment" and encumbrance amount, and here we also check if we have some unapplied to add
  public static void updateNewAmountWithUnappliedCreditOnApproval(Transaction encumbrance, CurrencyUnit currency,
                                                                  double awaitingPayment, double newAmount) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    if (initialAmount > 0 && awaitingPayment < 0) {
      double newAmountUnapplied = sumMoney(newAmount, Math.abs(awaitingPayment), currency);
      if (newAmountUnapplied > 0 && newAmountUnapplied <= initialAmount) {
        log.info("updateNewAmountWithUnappliedCreditOnApproval:: Encumbrance initialAmount={} oldAmount={} awaitingPayment={} newAmountUnapplied={}",
          initialAmount, encumbrance.getAmount(), awaitingPayment, newAmountUnapplied);
        encumbrance.setAmount(newAmountUnapplied);
      }
    } else {
      encumbrance.setAmount(newAmount);
    }
  }

  // Payment operation does not directly affect encumbrance amount, it just swifts the amount from "awaiting payment" to "expended" in most situations,
  // but if the "available" is exceeding the "initial encumbered amount" and our "credited" is unapplied, we should add the unapplied credit here
  public static void updateNewAmountWithUnappliedCreditOnPayment(Transaction encumbrance, CurrencyUnit currency,
                                                                 double amount, double newAwaitingPayment, double newExpended, double newCredited) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    if (initialAmount > 0 && newCredited > 0) {
      double oldAmount = encumbrance.getAmount();
      double available = sumMoney(currency, oldAmount, newAwaitingPayment, newExpended, newCredited);
      double newAmountUnapplied = calculateNewAmount(encumbrance, currency, newAwaitingPayment, newExpended, newCredited);
      log.info("updateNewAmountWithUnappliedCreditOnPayment:: Encumbrance initialAmount={} oldAmount={} awaitingPayment={} expended={} credited={} amount={} " +
          "available={} newAmountUnapplied={}", initialAmount, oldAmount, newAwaitingPayment, newExpended, newCredited, amount, available, newAmountUnapplied);
      if (available > initialAmount && (newAmountUnapplied > 0 && newAmountUnapplied <= initialAmount)) {
        encumbrance.setAmount(newAmountUnapplied);
      }
    }
  }

  // Here we calculate the new amount based on the facts at hand, and in case of negative amount we default them immediately at 0
  public static double calculateNewAmountDefaultOnZero(Transaction encumbrance, CurrencyUnit currency,
                                                       double awaitingPayment, double expended, double credited) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    double newAmount = subtractMoneyOrDefault(initialAmount, awaitingPayment, 0d, currency);
    newAmount = subtractMoneyOrDefault(newAmount, expended, 0d, currency);
    newAmount = sumMoney(newAmount, credited, currency);
    log.info("calculateNewAmountDefaultOnZero:: Encumbrance initialAmount={} oldAmount={} awaitingPayment={} expended={} " +
        "credited={} newAmount={}", initialAmount, encumbrance.getAmount(), awaitingPayment, expended, credited, newAmount);
    return newAmount;
  }

  // Here we calculate the new amount based on the facts at hand, and in case of negative amount we delegate it to be handled downstream
  public static double calculateNewAmount(Transaction encumbrance, CurrencyUnit currency,
                                          double awaitingPayment, double expended, double credited) {
    double initialAmount = encumbrance.getEncumbrance().getInitialAmountEncumbered();
    double newAmount = subtractMoney(initialAmount, awaitingPayment, currency);
    newAmount = subtractMoney(newAmount, expended, currency);
    newAmount = sumMoney(newAmount, credited, currency);
    log.info("calculateNewAmount:: Encumbrance initialAmount={} oldAmount={} awaitingPayment={} expended={} " +
        "credited={} newAmount={}", initialAmount, encumbrance.getAmount(), awaitingPayment, expended, credited, newAmount);
    return newAmount;
  }

  // In most situation we should safeguard the encumbrance amount from potential flawed calculations in the system by defaulting
  // the amount to either 0 or at the "initial encumbered amount" which is our upper limit that we should not be exceeding
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
