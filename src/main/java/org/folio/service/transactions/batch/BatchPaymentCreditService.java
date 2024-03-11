package org.folio.service.transactions.batch;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.utils.MoneyUtils;
import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.List;
import java.util.Map;

import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.utils.CalculationUtils.calculateBudgetSummaryFields;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.sumMoney;

public class BatchPaymentCreditService extends AbstractBatchTransactionService {

  @Override
  public TransactionType getTransactionType() {
    return PAYMENT;
  }

  @Override
  public void prepareCreatingTransactions(List<Transaction> transactionsToCreate, BatchTransactionHolder holder) {
    updateEncumbrances(transactionsToCreate, holder);
    updateBudgets(transactionsToCreate, holder);
    markPendingPaymentsForDeletion(holder);
  }

  @Override
  public void prepareUpdatingTransactions(List<Transaction> transactionsToUpdate, BatchTransactionHolder holder) {
    updateEncumbrances(transactionsToUpdate, holder);
    updateBudgets(transactionsToUpdate, holder);
  }

  private void updateEncumbrances(List<Transaction> transactions, BatchTransactionHolder holder) {
    List<Transaction> encumbrances = prepareEncumbrancesToProcess(transactions, holder, Transaction::getPaymentEncumbranceId);
    if (encumbrances.isEmpty()) {
      return;
    }
    Map<String, Transaction> encumbranceMap = holder.getLinkedEncumbranceMap();
    Map<String, Transaction> existingTransactionMap = holder.getExistingTransactionMap();
    List<Transaction> transactionsWithAnEncumbrance = transactions.stream()
      .filter(tr -> tr.getPaymentEncumbranceId() != null)
      .sorted((tr1, tr2) -> {
        // sort to process payments before credits
        if (tr1.getTransactionType() == tr2.getTransactionType()) {
          return 0;
        } else if (tr1.getTransactionType() == PAYMENT) {
          return -1;
        } else {
          return 1;
        }
      })
      .toList();
    transactionsWithAnEncumbrance.forEach(tr -> {
      CurrencyUnit currency = Monetary.getCurrency(tr.getCurrency());
      Transaction encumbrance = encumbranceMap.get(tr.getPaymentEncumbranceId());
      if (cancelledTransaction(tr, existingTransactionMap)) {
        updateEncumbranceToCancelTransaction(tr, encumbrance, currency);
      } else {
        updateEncumbranceToApplyTransaction(tr, encumbrance, currency, existingTransactionMap);
      }
    });
  }

  private void updateEncumbranceToCancelTransaction(Transaction transaction, Transaction encumbrance, CurrencyUnit currency) {
    // NOTE: we can't unrelease the encumbrance automatically because the payment/credit does not say if the encumbrance
    // was released automatically or not (as opposed to a pending payment).
    double expended = encumbrance.getEncumbrance().getAmountExpended();
    double amount = encumbrance.getAmount();
    if (transaction.getTransactionType() == PAYMENT) {
      expended = subtractMoney(expended, transaction.getAmount(), currency);
      amount = sumMoney(amount, transaction.getAmount(), currency);
    } else {
      expended = sumMoney(expended, transaction.getAmount(), currency);
      if (Encumbrance.Status.RELEASED != encumbrance.getEncumbrance().getStatus()) {
        amount = subtractMoney(amount, transaction.getAmount(), currency);
      }
    }
    encumbrance.getEncumbrance().setAmountExpended(expended);
    encumbrance.setAmount(amount);
  }

  private void updateEncumbranceToApplyTransaction(Transaction transaction, Transaction encumbrance, CurrencyUnit currency,
      Map<String, Transaction> existingTransactionMap) {
    MonetaryAmount expended = Money.of(encumbrance.getEncumbrance().getAmountExpended(), currency);
    MonetaryAmount awaitingPayment = Money.of(encumbrance.getEncumbrance().getAmountAwaitingPayment(), currency);
    MonetaryAmount amount = Money.of(transaction.getAmount(), currency);
    Transaction existingTransaction = existingTransactionMap.get(transaction.getId());
    if (existingTransaction != null) {
      amount = amount.subtract(Money.of(existingTransaction.getAmount(), currency));
    }
    if (transaction.getTransactionType() == PAYMENT) {
      expended = expended.add(amount);
      awaitingPayment = awaitingPayment.subtract(amount);
    } else {
      expended = expended.subtract(amount);
      awaitingPayment = awaitingPayment.add(amount);
    }
    encumbrance.getEncumbrance()
      .withAmountExpended(expended.getNumber().doubleValue())
      .withAmountAwaitingPayment(awaitingPayment.getNumber().doubleValue());
  }

  private void updateBudgets(List<Transaction> transactions, BatchTransactionHolder holder) {
    Map<Budget, List<Transaction>> budgetToTransactions = createBudgetMapForTransactions(transactions, holder.getBudgets());
    if (budgetToTransactions.isEmpty()) {
      return;
    }
    Map<String, Transaction>  existingTransactionMap = holder.getExistingTransactionMap();
    budgetToTransactions.forEach((budget, transactionsForBudget) -> {
      transactionsForBudget.forEach(transaction -> {
        if (cancelledTransaction(transaction, existingTransactionMap)) {
          cancelTransaction(budget, transaction);
        } else {
          applyTransaction(budget, transaction, existingTransactionMap.get(transaction.getId()));
        }
      });
      calculateBudgetSummaryFields(budget);
      updateBudgetMetadata(budget, transactionsForBudget.get(0));
    });
  }

  private void cancelTransaction(Budget budget, Transaction transaction) {
    double expenditures = budget.getExpenditures();
    CurrencyUnit currency = Monetary.getCurrency(transaction.getCurrency());
    if (transaction.getTransactionType() == PAYMENT) {
      expenditures = MoneyUtils.subtractMoney(expenditures, transaction.getAmount(), currency);
    } else {
      expenditures = MoneyUtils.sumMoney(expenditures, transaction.getAmount(), currency);
    }
    transaction.setVoidedAmount(transaction.getAmount());
    transaction.setAmount(0d);
    budget.setExpenditures(expenditures);
  }

  private void applyTransaction(Budget budget, Transaction transaction, Transaction existingTransaction) {
    CurrencyUnit currency = Monetary.getCurrency(transaction.getCurrency());
    MonetaryAmount expenditures = Money.of(budget.getExpenditures(), currency);
    MonetaryAmount awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    MonetaryAmount amount = Money.of(transaction.getAmount(), currency);
    if (existingTransaction != null) {
      amount = amount.subtract(Money.of(existingTransaction.getAmount(), currency));
    }
    if (transaction.getTransactionType() == PAYMENT) {
      expenditures = expenditures.add(amount);
      awaitingPayment = awaitingPayment.subtract(amount);
    } else {
      expenditures = expenditures.subtract(amount);
      awaitingPayment = awaitingPayment.add(amount);
    }
    budget.setExpenditures(expenditures.getNumber().doubleValue());
    budget.setAwaitingPayment(awaitingPayment.getNumber().doubleValue());
  }

  private void markPendingPaymentsForDeletion(BatchTransactionHolder holder) {
    List<Transaction> linkedPendingPayments = holder.getLinkedPendingPayments();
    linkedPendingPayments.forEach(tr -> holder.addTransactionToDelete(tr.getId()));
  }

}
