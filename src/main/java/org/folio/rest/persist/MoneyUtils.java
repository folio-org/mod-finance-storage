package org.folio.rest.persist;

import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;

public class MoneyUtils {

  private MoneyUtils() {
  }

  public static Double subtractMoney(Double encumbered, Double subtrahend, CurrencyUnit currency) {
    return Money.of(encumbered, currency)
      .subtract(Money.of(subtrahend, currency))
      .getNumber()
      .doubleValue();
  }

  public static Double sumMoney(Double encumbered, Double amount, CurrencyUnit currency) {
    return Money.of(encumbered, currency)
      .add(Money.of(amount, currency))
      .getNumber()
      .doubleValue();
  }
}
