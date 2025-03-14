package org.folio.service.transactions.batch;

import io.vertx.core.json.JsonObject;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNullElse;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.CREDIT;

public abstract class AbstractBatchTransactionService implements BatchTransactionServiceInterface {

  @Override
  public void prepareDeletingTransactions(List<Transaction> transactionsToDelete, BatchTransactionHolder holder) {
  }

  Map<Budget, List<Transaction>> createBudgetMapForTransactions(List<Transaction> transactions, List<Budget> budgets) {
    return transactions.stream()
      .collect(groupingBy(tr -> getBudgetForTransaction(budgets, tr)))
      .entrySet()
      .stream()
      .filter(entry -> entry.getKey().isPresent())
      .collect(toMap(entry -> entry.getKey().get(), Map.Entry::getValue));
  }

  Optional<Budget> getBudgetForTransaction(List<Budget> budgets, Transaction tr) {
    String fundId = tr.getTransactionType() == CREDIT ? tr.getToFundId() : tr.getFromFundId();
    String fiscalYearId = tr.getFiscalYearId();
    Optional<Budget> optionalBudget = budgets.stream()
      .filter(b -> b.getFundId().equals(fundId) && b.getFiscalYearId().equals(fiscalYearId))
      .findFirst();
    if (optionalBudget.isEmpty()) {
      throw new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(),
        String.format("Warning: could not find budget for transaction with type %s, id=%s", tr.getTransactionType(), tr.getId()));
    }
    return optionalBudget;
  }

  Map<Budget, List<Transaction>> createBudgetMapForAllocationsAndTransfers(List<Transaction> transactions, List<Budget> budgets) {
    return budgets.stream()
      .collect(toMap(b -> b, b -> transactions.stream()
        .filter(tr -> (b.getFundId().equals(tr.getFromFundId()) || b.getFundId().equals(tr.getToFundId())) &&
          b.getFiscalYearId().equals(tr.getFiscalYearId()))
        .toList()
      ));
  }

  boolean cancelledTransaction(Transaction transaction, Map<String, Transaction> existingTransactionMap) {
    if (!TRUE.equals(transaction.getInvoiceCancelled())) {
      return false;
    }
    String id = transaction.getId();
    Transaction existingTransaction = existingTransactionMap.get(id);
    return existingTransaction != null && !TRUE.equals(existingTransaction.getInvoiceCancelled());
  }

  void updateBudgetMetadata(Budget budget, Transaction transaction) {
    budget.getMetadata().setUpdatedDate(transaction.getMetadata().getUpdatedDate());
    budget.getMetadata().setUpdatedByUserId(transaction.getMetadata().getUpdatedByUserId());
  }

  List<Transaction> prepareEncumbrancesToProcess(List<Transaction> transactions, BatchTransactionHolder holder,
      Function<Transaction, String> transactionToEncumbranceId) {
    Map<String, Transaction> linkedEncumbranceMap = holder.getLinkedEncumbranceMap();
    List<Transaction> encumbrances = transactions.stream()
      .map(transactionToEncumbranceId)
      .filter(Objects::nonNull)
      .distinct()
      .map(linkedEncumbranceMap::get)
      .toList();
    if (encumbrances.isEmpty()) {
      return encumbrances;
    }
    // if an encumbrance  is already marked for update, use the object from transactionsToUpdate to continue
    Map<String, Transaction> transactionsToUpdateMap = holder.getAllTransactionsToUpdate().stream()
      .collect(toMap(Transaction::getId, identity()));
    encumbrances = encumbrances.stream()
      .map(tr -> requireNonNullElse(transactionsToUpdateMap.get(tr.getId()), tr))
      .toList();
    // otherwise add it, so it gets saved (assuming encumbrances are processed after pending payments)
    encumbrances.stream()
      .filter(tr -> !transactionsToUpdateMap.containsKey(tr.getId()))
      .forEach(tr -> holder.addTransactionToUpdate(tr, JsonObject.mapFrom(tr).mapTo(Transaction.class)));

    return encumbrances;
  }
}
