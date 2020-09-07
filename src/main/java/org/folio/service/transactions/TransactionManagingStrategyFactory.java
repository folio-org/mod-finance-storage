package org.folio.service.transactions;

import org.folio.rest.jaxrs.model.Transaction;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class TransactionManagingStrategyFactory {

  private Map<Transaction.TransactionType, TransactionManagingStrategy> strategies;

  public TransactionManagingStrategyFactory(Set<TransactionManagingStrategy> strategySet) {
    createStrategy(strategySet);
  }

  public TransactionManagingStrategy findStrategy(Transaction.TransactionType transactionType) {
    Transaction.TransactionType type = transactionType == Transaction.TransactionType.CREDIT ? Transaction.TransactionType.PAYMENT : transactionType;
    return strategies.get(type);
  }

  private void createStrategy(Set<TransactionManagingStrategy> strategySet) {
    strategies = new EnumMap<>(Transaction.TransactionType.class);
    strategySet.forEach(
      strategy -> strategies.put(strategy.getStrategyName(), strategy));
  }

}
