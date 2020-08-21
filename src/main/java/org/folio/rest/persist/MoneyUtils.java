package org.folio.rest.persist;

import java.math.BigDecimal;
import java.util.stream.Stream;

import javax.money.CurrencyUnit;

import org.javamoney.moneta.Money;

public final class MoneyUtils {

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
    return BigDecimal.valueOf(subtractMoney(minuend, subtrahend, currency)).max(BigDecimal.ZERO).doubleValue();
  }

  public static Double sumMoney(CurrencyUnit currency, Double ... values) {
    return Stream.of(values)
      .map(aDouble -> Money.of(aDouble, currency))
      .reduce(Money::add)
      .orElse(Money.zero(currency))
      .getNumber()
      .doubleValue();
  }
}
