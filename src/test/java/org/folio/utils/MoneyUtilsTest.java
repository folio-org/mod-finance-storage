package org.folio.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import java.util.Arrays;
import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;

public class MoneyUtilsTest {

  @Test
  void testCalculateExpendedPercentage() {
    MonetaryAmount expended = Money.of(50, "USD");
    double totalExpended = 200;
    double result = MoneyUtils.calculateExpendedPercentage(expended, totalExpended);
    assertEquals(25.0, result, 0.01);
  }

  @Test
  void testCalculateCreditedPercentage() {
    MonetaryAmount credited = Money.of(100, "USD");
    double totalCredited = 400;
    double result = MoneyUtils.calculateCreditedPercentage(credited, totalCredited);
    assertEquals(25.0, result, 0.01);
  }

  @Test
  void testCalculateTotalAmount() {
    List<Transaction> transactions = Arrays.asList(
      new Transaction().withAmount(50.00),
      new Transaction().withAmount(30.00),
      new Transaction().withAmount(20.00)
    );
    CurrencyUnit currency = Monetary.getCurrency("USD");
    MonetaryAmount totalAmount = MoneyUtils.calculateTotalAmount(transactions, currency);
    assertEquals(Money.of(100.00, currency), totalAmount);
  }

  @Test
  void testCalculateTotalAmountWithRounding() {
    List<Transaction> transactions = Arrays.asList(
      new Transaction().withAmount(50.005),
      new Transaction().withAmount(30.005),
      new Transaction().withAmount(20.005)
    );

    CurrencyUnit currency = Monetary.getCurrency("USD");
    double totalAmountWithRounding = MoneyUtils.calculateTotalAmountWithRounding(transactions, currency);
    assertEquals(100.01, totalAmountWithRounding, 0.01);
  }

}
