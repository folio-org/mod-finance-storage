package org.folio.service.transactions;

import org.folio.rest.jaxrs.model.Transaction.TransactionType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class TransactionManagingStrategyFactory {

  private Map<TransactionType, TransactionManagingStrategy> strategies;

  public TransactionManagingStrategyFactory(Set<TransactionManagingStrategy> strategySet) {
    createStrategy(strategySet);
  }

  public static TransactionType transactionTypeWithoutCredit(TransactionType transactionType) {
    return transactionType == TransactionType.CREDIT ? TransactionType.PAYMENT : transactionType;
  }

  public TransactionManagingStrategy findStrategy(TransactionType transactionType) {
    return strategies.get(transactionTypeWithoutCredit(transactionType));
  }

  private void createStrategy(Set<TransactionManagingStrategy> strategySet) {
    strategies = new EnumMap<>(TransactionType.class);
    strategySet.forEach(
      strategy -> strategies.put(strategy.getStrategyName(), strategy));
  }

}
