package org.folio.service.transactions;

import static java.util.Collections.singletonList;
import static org.folio.rest.util.ErrorCodes.MISSING_FUND_ID;
import static org.folio.rest.util.ErrorCodes.MUST_BE_POSITIVE;

import java.util.List;
import java.util.UUID;

import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.service.budget.BudgetService;
import org.folio.utils.CalculationUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class AllocationService extends DefaultTransactionService implements TransactionManagingStrategy {

  private final BudgetService budgetService;

  public AllocationService(BudgetService budgetService) {
    this.budgetService = budgetService;
  }

  @Override
  public Transaction.TransactionType getStrategyName() {
    return Transaction.TransactionType.ALLOCATION;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction allocation, RequestContext requestContext) {
    Promise<Transaction> promise = Promise.promise();

    try {
      handleValidationError(allocation);
    } catch (HttpException e) {
      return Future.failedFuture(e);
    }
    DBClient client = requestContext.toDBClient();

    client.startTx()
      .compose(v -> budgetService.checkBudgetHaveMoneyForTransaction(allocation, client))
      .compose(v -> createAllocation(allocation, client))
      .compose(v -> updateFromBudget(allocation, client))
      .compose(v -> updateBudgetTo(allocation, client))
      .compose(ok -> client.endTx())
      .onComplete(result -> {
        if (result.failed()) {
          log.error("Transfer or associated data failed to be processed", result.cause());
          client.rollbackTransaction();
          promise.fail(result.cause());
        } else {
          promise.complete(allocation);
          log.info("Transactions and associated data were successfully processed");
        }
      });
    return promise.future();
  }

  private Future<Transaction> createAllocation(Transaction transaction, DBClient client) {
    Promise<Transaction> promise = Promise.promise();
    if (StringUtils.isEmpty(transaction.getId())) {
      transaction.setId(UUID.randomUUID()
        .toString());
    }
    transaction.setVersion(1);

    client.getPgClient()
      .save(client.getConnection(), TRANSACTION_TABLE, transaction.getId(), transaction, event -> {
        if (event.succeeded()) {
          promise.complete(transaction);
        } else {
          promise.fail(new HttpException(500, PgExceptionUtil.getMessage(event.cause())));
        }
      });
    return promise.future();
  }

  private Future<Void> updateBudgetTo(Transaction allocation, DBClient client) {
    if (StringUtils.isEmpty(allocation.getToFundId())) {
      return Future.succeededFuture();
    }
    return budgetService.getBudgetByFiscalYearIdAndFundIdForUpdate(allocation.getFiscalYearId(), allocation.getToFundId(), client)
      .map(budgetTo -> {
        Budget budgetToNew = JsonObject.mapFrom(budgetTo)
          .mapTo(Budget.class);
        CalculationUtils.recalculateBudgetAllocationTo(budgetToNew, allocation, allocation.getAmount());
        budgetService.updateBudgetMetadata(budgetToNew, allocation);
        return budgetToNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(singletonList(budgetFrom), client))
      .map(i -> null);
  }

  private Future<Void> updateFromBudget(Transaction allocation, DBClient client) {
    if (StringUtils.isEmpty(allocation.getFromFundId())) {
      return Future.succeededFuture();
    }
    return budgetService.getBudgetByFiscalYearIdAndFundIdForUpdate(allocation.getFiscalYearId(), allocation.getFromFundId(), client)
      .map(budgetFrom -> {
        Budget budgetFromNew = JsonObject.mapFrom(budgetFrom)
          .mapTo(Budget.class);
        CalculationUtils.recalculateBudgetAllocationFrom(budgetFromNew, allocation, allocation.getAmount());
        budgetService.updateBudgetMetadata(budgetFromNew, allocation);
        return budgetFromNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(singletonList(budgetFrom), client))
      .map(i -> null);
  }

  private void handleValidationError(Transaction transaction) {
    checkRequiredFields(transaction);
    checkAmount(transaction);
  }

  private void checkAmount(Transaction transaction) {
    if (transaction.getAmount() <= 0) {
      List<Parameter> parameters = singletonList(new Parameter().withKey("fieldName")
        .withValue("amount"));
      Errors errors = new Errors().withErrors(singletonList(MUST_BE_POSITIVE.toError()
        .withParameters(parameters)))
        .withTotalRecords(1);
      throw new HttpException(422, JsonObject.mapFrom(errors)
        .encode());
    }
  }

  private void checkRequiredFields(Transaction transaction) {
    if (transaction.getToFundId() == null && transaction.getFromFundId() == null) {
      throw new HttpException(422, JsonObject.mapFrom(new Errors().withErrors(singletonList(MISSING_FUND_ID.toError()))
        .withTotalRecords(1))
        .encode());
    }
  }

}
