package org.folio.service.transactions.batch;

import lombok.extern.log4j.Log4j2;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.util.List;
import java.util.Map;

import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.service.transactions.batch.EncumbranceUtil.calculateNewAmountDefaultOnZero;
import static org.folio.service.transactions.batch.EncumbranceUtil.updateNewAmountWithUnappliedCreditOnPayment;
import static org.folio.utils.CalculationUtils.calculateBudgetSummaryFields;
import static org.folio.utils.MoneyUtils.subtractMoney;
import static org.folio.utils.MoneyUtils.subtractMoneyOrDefault;
import static org.folio.utils.MoneyUtils.sumMoney;

@Log4j2
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
    double awaitingPayment = encumbrance.getEncumbrance().getAmountAwaitingPayment();
    double expended = encumbrance.getEncumbrance().getAmountExpended();
    double credited = encumbrance.getEncumbrance().getAmountCredited();
    double amount = encumbrance.getAmount();
    double newExpended = expended;
    double newCredited = credited;
    if (transaction.getTransactionType() == PAYMENT) {
      newExpended = subtractMoney(expended, transaction.getAmount(), currency);
      if (encumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED) {
        amount = calculateNewAmountDefaultOnZero(encumbrance, currency, awaitingPayment, newExpended, newCredited);
      }
    } else {
      newCredited = subtractMoneyOrDefault(credited, transaction.getAmount(), 0d, currency);
      if (encumbrance.getEncumbrance().getStatus() == Encumbrance.Status.UNRELEASED
        && (awaitingPayment > 0 || newExpended > 0 || newCredited > 0)) {
        amount = subtractMoneyOrDefault(amount, transaction.getAmount(), 0d, currency);
      }
    }
    log.info("updateEncumbranceToCancelTransaction:: Expended oldAmount={} amount={} newAmount={}", expended, amount, newExpended);
    log.info("updateEncumbranceToCancelTransaction:: Credited oldAmount={} amount={} newAmount={}", credited, amount, newCredited);
    encumbrance.getEncumbrance().setAmountExpended(newExpended);
    encumbrance.getEncumbrance().setAmountCredited(newCredited);
    encumbrance.setAmount(amount);
  }

  private void updateEncumbranceToApplyTransaction(Transaction transaction, Transaction encumbrance, CurrencyUnit currency,
                                                   Map<String, Transaction> existingTransactionMap) {
    double awaitingPayment = encumbrance.getEncumbrance().getAmountAwaitingPayment();
    double expended = encumbrance.getEncumbrance().getAmountExpended();
    double credited = encumbrance.getEncumbrance().getAmountCredited();
    double amount = transaction.getAmount();
    Transaction existingTransaction = existingTransactionMap.get(transaction.getId());
    if (existingTransaction != null) {
      amount = subtractMoney(amount, existingTransaction.getAmount(), currency);
    }
    double newAwaitingPayment;
    double newExpended = expended;
    double newCredited = credited;
    if (transaction.getTransactionType() == PAYMENT) {
      newAwaitingPayment = subtractMoney(awaitingPayment, amount, currency);
      newExpended = sumMoney(expended, amount, currency);
      // add the unapplied credit amount back to the encumbrance on invoice payment if the encumbrance amount was defaulted at the initial amount
      updateNewAmountWithUnappliedCreditOnPayment(encumbrance, currency, amount, newAwaitingPayment, newExpended, newCredited);
    } else {
      newAwaitingPayment = sumMoney(awaitingPayment, amount, currency);
      newCredited = sumMoney(credited, amount, currency);
    }
    log.info("updateEncumbranceToApplyTransaction:: Awaiting payment oldAmount={} amount={} newAmount={}", awaitingPayment, amount, newAwaitingPayment);
    log.info("updateEncumbranceToApplyTransaction:: Expended oldAmount={} amount={} newAmount={}", expended, amount, newExpended);
    log.info("updateEncumbranceToApplyTransaction:: Credited oldAmount={} amount={} newAmount={}", credited, amount, newCredited);
    encumbrance.getEncumbrance()
      .withAmountExpended(newExpended)
      .withAmountCredited(newCredited)
      .withAmountAwaitingPayment(newAwaitingPayment);
  }

  private void updateBudgets(List<Transaction> transactions, BatchTransactionHolder holder) {
    Map<Budget, List<Transaction>> budgetToTransactions = createBudgetMapForTransactions(transactions, holder.getBudgets());
    if (budgetToTransactions.isEmpty()) {
      return;
    }
    Map<String, Transaction> existingTransactionMap = holder.getExistingTransactionMap();
    budgetToTransactions.forEach((budget, transactionsForBudget) -> {
      transactionsForBudget.forEach(transaction -> {
        if (cancelledTransaction(transaction, existingTransactionMap)) {
          cancelTransaction(budget, transaction);
        } else {
          applyTransaction(budget, transaction, existingTransactionMap.get(transaction.getId()));
        }
      });
      calculateBudgetSummaryFields(budget);
      updateBudgetMetadata(budget, transactionsForBudget.getFirst());
    });
  }

  private void cancelTransaction(Budget budget, Transaction transaction) {
    double expenditures = budget.getExpenditures();
    double credits = budget.getCredits();
    CurrencyUnit currency = Monetary.getCurrency(transaction.getCurrency());
    if (transaction.getTransactionType() == PAYMENT) {
      expenditures = subtractMoney(expenditures, transaction.getAmount(), currency);
    } else {
      credits = subtractMoney(credits, transaction.getAmount(), currency);
    }
    transaction.setVoidedAmount(transaction.getAmount());
    transaction.setAmount(0d);
    budget.setExpenditures(expenditures);
    budget.setCredits(credits);
  }

  private void applyTransaction(Budget budget, Transaction transaction, Transaction existingTransaction) {
    CurrencyUnit currency = Monetary.getCurrency(transaction.getCurrency());
    MonetaryAmount expenditures = Money.of(budget.getExpenditures(), currency);
    MonetaryAmount credits = Money.of(budget.getCredits(), currency);
    MonetaryAmount awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    MonetaryAmount amount = Money.of(transaction.getAmount(), currency);
    if (existingTransaction != null) {
      amount = amount.subtract(Money.of(existingTransaction.getAmount(), currency));
    }
    if (transaction.getTransactionType() == PAYMENT) {
      expenditures = expenditures.add(amount);
      awaitingPayment = awaitingPayment.subtract(amount);
    } else {
      credits = credits.add(amount);
      awaitingPayment = awaitingPayment.add(amount);
    }
    budget.setExpenditures(expenditures.getNumber().doubleValue());
    budget.setCredits(credits.getNumber().doubleValue());
    budget.setAwaitingPayment(awaitingPayment.getNumber().doubleValue());
  }

  private void markPendingPaymentsForDeletion(BatchTransactionHolder holder) {
    List<Transaction> linkedPendingPayments = holder.getLinkedPendingPayments();
    linkedPendingPayments.forEach(holder::addTransactionToDeleteWithoutProcessing);
  }
}
