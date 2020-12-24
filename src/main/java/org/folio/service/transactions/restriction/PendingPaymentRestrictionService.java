package org.folio.service.transactions.restriction;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.AwaitingPayment;
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
import java.util.Optional;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang.StringUtils.EMPTY;
import static org.folio.rest.persist.HelperUtils.buildNullValidationError;
import static org.folio.service.transactions.AbstractTransactionService.FROM_FUND_ID;

public class PendingPaymentRestrictionService extends BaseTransactionRestrictionService {

  private final TransactionDAO transactionsDAO;

  public PendingPaymentRestrictionService(BudgetService budgetService, LedgerService ledgerService, TransactionDAO transactionsDAO) {
    super(budgetService, ledgerService);
    this.transactionsDAO = transactionsDAO;
  }

  /**
   * Calculates remaining amount for payment and pending payments [remaining amount] = (allocated * allowableExpenditure) - (allocated
   * - (unavailable + available)) - (encumbered + expended + awaitingPayment - relatedEncumbered)
   *
   * @param budget             processed budget
   * @param currency
   * @param relatedTransaction
   * @return remaining amount for payment
   */
  @Override
  public Money getBudgetRemainingAmount(Budget budget, String currency, Transaction relatedTransaction) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableExpenditure from percentage value
    double allowableExpenditure = Money.of(budget.getAllowableExpenditure(), currency).divide(100d).getNumber().doubleValue();
    Money expenditure = Money.of(budget.getExpenditures(), currency);
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    Money relatedEncumbered = relatedTransaction == null ? Money.of(0d, currency) : Money.of(relatedTransaction.getAmount(), currency);
    Money netTransfers = Money.of(budget.getNetTransfers(), currency);

    Money result = allocated.add(netTransfers).multiply(allowableExpenditure);
    result = result.subtract(encumbered.add(expenditure.add(awaitingPayment)).subtract(relatedEncumbered));

    return result;
  }

  @Override
  protected Future<Transaction> getRelatedTransaction(Transaction transaction, DBClient client) {

    String encumbranceId = Optional.ofNullable(transaction)
      .map(Transaction::getAwaitingPayment)
      .map(AwaitingPayment::getEncumbranceId)
      .orElse(EMPTY);

    if (encumbranceId.isEmpty()) {
      return Future.succeededFuture(null);
    }

    CriterionBuilder criterion = new CriterionBuilder()
      .with("id", encumbranceId);

    return transactionsDAO.getTransactions(criterion.build(), client)
      .map(transactions -> transactions.isEmpty() ? null : transactions.get(0));
  }

  @Override
  boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget) {
    return ledger.getRestrictExpenditures() && budget.getAllowableExpenditure() != null;
  }

  @Override
  public Void handleValidationError(Transaction transaction) {

    List<Error> errors = new ArrayList<>(buildNullValidationError(transaction.getFromFundId(), FROM_FUND_ID));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }

}
