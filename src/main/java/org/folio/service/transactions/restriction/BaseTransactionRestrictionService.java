package org.folio.service.transactions.restriction;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.ledger.LedgerService;
import org.javamoney.moneta.Money;

import javax.ws.rs.core.Response;
import java.text.MessageFormat;

import static org.folio.rest.util.ErrorCodes.BUDGET_IS_INACTIVE;

public abstract class BaseTransactionRestrictionService implements TransactionRestrictionService {

  private static final String BUDGET_ID = "budgetId";
  public static final String FUND_CANNOT_BE_PAID = "Fund cannot be paid due to restrictions";

  protected final Logger log = LogManager.getLogger(this.getClass());

  private final BudgetService budgetService;
  private final LedgerService ledgerService;

  public BaseTransactionRestrictionService(BudgetService budgetService, LedgerService ledgerService) {
    this.budgetService = budgetService;
    this.ledgerService = ledgerService;
  }


  @Override
  public Future<Void> verifyBudgetHasEnoughMoney(Transaction transaction, DBClient dbClient) {
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();

    return budgetService.getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), fundId, dbClient, false)
      .compose(budget -> {
        if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
          Error error = buildBudgetIsInactiveError(budget);
          log.error(error.getMessage());
          return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), error));
        }
        if (transaction.getTransactionType() == Transaction.TransactionType.CREDIT || transaction.getAmount() <= 0) {
          return Future.succeededFuture();
        }
        return getRelatedTransaction(transaction, dbClient)
          .compose(relatedTransaction -> ledgerService.getLedgerByTransaction(transaction, dbClient)
            .map(ledger -> checkTransactionAllowed(transaction, relatedTransaction, budget, ledger)));
      });
  }

  private Error buildBudgetIsInactiveError(Budget budget) {
    String description = MessageFormat.format(BUDGET_IS_INACTIVE.getDescription(), budget.getId());
    Error error = new Error().withCode(BUDGET_IS_INACTIVE.getCode()).withMessage(description);
    error.getParameters().add(new Parameter().withKey(BUDGET_ID).withValue(budget.getId()));
    return error;
  }

  protected Future<Transaction> getRelatedTransaction(Transaction transaction, DBClient dbClient) {
    return Future.succeededFuture(null);
  }

  private Void checkTransactionAllowed(Transaction transaction, Transaction relatedTransaction, Budget budget, Ledger ledger) {
    if (isTransactionOverspendRestricted(ledger, budget)) {
      Money budgetRemainingAmount = getBudgetRemainingAmount(budget, transaction.getCurrency(), relatedTransaction);
      if (Money.of(transaction.getAmount(), transaction.getCurrency()).isGreaterThan(budgetRemainingAmount)) {
        throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), FUND_CANNOT_BE_PAID);
      }
    }
    return null;
  }

  abstract Money getBudgetRemainingAmount(Budget budget, String currency, Transaction relatedTransaction);
  abstract boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget);
}
