package org.folio.service.transactions.restriction;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.ledger.LedgerService;
import org.javamoney.moneta.Money;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.persist.HelperUtils.buildNullValidationError;
import static org.folio.service.transactions.AbstractTransactionService.FROM_FUND_ID;
import static org.folio.service.transactions.AbstractTransactionService.TO_FUND_ID;

public class PaymentCreditRestrictionService extends BaseTransactionRestrictionService {

  private final TransactionDAO transactionDAO;

  public PaymentCreditRestrictionService(BudgetService budgetService, LedgerService ledgerService, TransactionDAO transactionDAO) {
    super(budgetService, ledgerService);
    this.transactionDAO = transactionDAO;
  }

  /**
   * Calculates remaining amount for payment [remaining amount] = (allocated * allowableExpenditure) - (allocated - (unavailable +
   * available)) - (expenditure + encumbered + awaitingPayment - relatedAwaitingPayment)
   *
   * @param budget             processed budget
   * @param currency
   * @param relatedTransaction
   * @return remaining amount for payment
   */
  @Override
  protected Money getBudgetRemainingAmount(Budget budget, String currency, Transaction relatedTransaction) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableExpenditure from percentage value
    double allowableExpenditure = Money.of(budget.getAllowableExpenditure(), currency).divide(100d).getNumber().doubleValue();
    Money unavailable = Money.of(budget.getUnavailable(), currency);
    Money available = Money.of(budget.getAvailable(), currency);
    Money expenditure = Money.of(budget.getExpenditures(), currency);
    Money relatedAwaitingPayment = relatedTransaction == null ? Money.of(0d, currency) : Money.of(relatedTransaction.getAmount(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money netTransfers = Money.of(budget.getNetTransfers(), currency);

    Money result = allocated.add(netTransfers).multiply(allowableExpenditure);
    result = result.subtract(allocated.subtract(unavailable.add(available)));
    result = result.subtract(expenditure.add(encumbered).add(awaitingPayment).subtract(relatedAwaitingPayment));

    return result;
  }

  @Override
  boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget) {
    return ledger.getRestrictExpenditures()
      && budget.getAllowableExpenditure() != null;
  }

  @Override
  public Void handleValidationError(Transaction transaction) {
    List<Error> errors = new ArrayList<>();

    if (transaction.getTransactionType() == Transaction.TransactionType.CREDIT) {
      errors.addAll(buildNullValidationError(transaction.getToFundId(), TO_FUND_ID));
    } else {
      errors.addAll(buildNullValidationError(transaction.getFromFundId(), FROM_FUND_ID));
    }
    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }

  @Override
  protected Future<Transaction> getRelatedTransaction(Transaction transaction, DBClient dbClient) {

    CriterionBuilder criterionBuilder;
    if (transaction.getSourceInvoiceLineId() != null) {
      criterionBuilder = new CriterionBuilder()
        .withJson("fromFundId","=", transaction.getFromFundId())
        .withJson("sourceInvoiceId","=", transaction.getSourceInvoiceId())
        .withJson("sourceInvoiceLineId","=", transaction.getSourceInvoiceLineId())
        .withJson("transactionType","=", PENDING_PAYMENT.value())
        .withOperation("AND");
    } else {
      criterionBuilder = new CriterionBuilder()
        .withJson("fromFundId","=", transaction.getFromFundId())
        .withJson("sourceInvoiceId","=", transaction.getSourceInvoiceId())
        .withJson("sourceInvoiceLineId","IS NULL", null)
        .withJson("transactionType","=", PENDING_PAYMENT.value())
        .withOperation("AND");
    }

    return transactionDAO.getTransactions(criterionBuilder.build(), dbClient)
      .map(transactions -> transactions.isEmpty() ? null : transactions.get(0));
  }

}
