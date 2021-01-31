package org.folio.service.transactions.restriction;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.service.budget.BudgetService;
import org.folio.service.ledger.LedgerService;
import org.javamoney.moneta.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.persist.HelperUtils.buildNullValidationError;

public class EncumbranceRestrictionService extends BaseTransactionRestrictionService {

  public EncumbranceRestrictionService(BudgetService budgetService, LedgerService ledgerService) {
    super(budgetService, ledgerService);
  }

  /**
   * Calculates remaining amount for encumbrance
   * [remaining amount] = (allocated + netTransfers) * allowableEncumbered - (encumbered + awaitingPayment + expended)
   *
   * @param budget   processed budget
   * @param currency
   * @param relatedTransaction
   * @return remaining amount for encumbrance
   */
  @Override
  Money getBudgetRemainingAmount(Budget budget, String currency, Transaction relatedTransaction) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableEncumbered converted from percentage value
    double allowableEncumbered = Money.of(budget.getAllowableEncumbrance(), currency).divide(100d).getNumber().doubleValue();
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    Money expended = Money.of(budget.getExpenditures(), currency);
    Money netTransfers = Money.of(budget.getNetTransfers(), currency);

    Money totalFunding = allocated.add(netTransfers);
    Money unavailable = encumbered.add(awaitingPayment).add(expended);

    return totalFunding.multiply(allowableEncumbered).subtract(unavailable);
  }

  @Override
  boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget) {
    return ledger.getRestrictEncumbrance()
      && budget.getAllowableEncumbrance() != null;
  }

  @Override
  public Void handleValidationError(Transaction transaction) {
    List<Error> errors = new ArrayList<>();

    errors.addAll(buildNullValidationError(getSummaryId(transaction), "encumbrance"));
    errors.addAll(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }

  private String getSummaryId(Transaction transaction) {
    return Optional.ofNullable(transaction.getEncumbrance())
      .map(Encumbrance::getSourcePurchaseOrderId)
      .orElse(null);
  }

}
