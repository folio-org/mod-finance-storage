package org.folio.service.transactions.restriction;

import static org.folio.service.transactions.AllOrNothingTransactionService.BUDGET_IS_INACTIVE;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.ledger.LedgerService;
import org.javamoney.moneta.Money;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;

public abstract class BaseTransactionRestrictionService implements TransactionRestrictionService {

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

    return budgetService.getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), fundId, dbClient)
      .compose(budget -> {
        if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
          log.error(BUDGET_IS_INACTIVE, budget.getId());
          return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_IS_INACTIVE));
        }
        if (transaction.getTransactionType() == Transaction.TransactionType.CREDIT || transaction.getAmount() <= 0) {
          return Future.succeededFuture();
        }
        return getRelatedTransaction(transaction, dbClient)
          .compose(relatedTransaction -> ledgerService.getLedgerByTransaction(transaction, dbClient)
            .map(ledger -> checkTransactionAllowed(transaction, relatedTransaction, budget, ledger)));
      });
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
