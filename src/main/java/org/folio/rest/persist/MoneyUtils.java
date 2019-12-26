package org.folio.rest.persist;

import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;

public class MoneyUtils {

  private MoneyUtils() {
  }

  public static Double subtractMoney(Double minuend, Double subtrahend, CurrencyUnit currency) {
    return Money.of(minuend, currency)
      .subtract(Money.of(subtrahend, currency))
      .getNumber()
      .doubleValue();
  }

  public static Double sumMoney(Double addend, Double amount, CurrencyUnit currency) {
    return Money.of(addend, currency)
      .add(Money.of(amount, currency))
      .getNumber()
      .doubleValue();
  }

  public static Double subtractMoneyNonNegative(Double minuend, Double subtrahend, CurrencyUnit currency) {
    return Math.max(subtractMoney(minuend, subtrahend, currency), 0d);
  }
}
