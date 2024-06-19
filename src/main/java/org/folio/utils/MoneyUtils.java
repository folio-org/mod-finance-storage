package org.folio.utils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.folio.rest.jaxrs.model.Transaction;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

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

  public static double calculateExpendedPercentage(MonetaryAmount expended, double totalExpended) {
    return expended.divide(totalExpended).multiply(100).with(Monetary.getDefaultRounding()).getNumber().doubleValue();
  }

  public static double calculateCreditedPercentage(MonetaryAmount credited, double totalCredited) {
    return credited.divide(totalCredited).multiply(100).with(Monetary.getDefaultRounding()).getNumber().doubleValue();
  }

  public static MonetaryAmount calculateTotalAmount(List<Transaction> transactions, CurrencyUnit currency) {
    return transactions.stream()
      .map(transaction -> (MonetaryAmount) Money.of(transaction.getAmount(), currency))
      .reduce(MonetaryFunctions::sum).orElse(Money.zero(currency));
  }

  public static double calculateTotalAmountWithRounding(List<Transaction> transactions, CurrencyUnit currency) {
    return calculateTotalAmount(transactions, currency).with(Monetary.getDefaultRounding()).getNumber().doubleValue();
  }
}
