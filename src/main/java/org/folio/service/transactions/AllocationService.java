package org.folio.service.transactions;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.persist.HelperUtils.buildNullValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.service.budget.BudgetService;
import org.folio.utils.CalculationUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

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
    } catch (HttpStatusException e) {
      return Future.failedFuture(e);
    }
    DBClient client = new DBClient(requestContext);

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

    client.getPgClient()
      .save(client.getConnection(), TRANSACTION_TABLE, transaction.getId(), transaction, event -> {
        if (event.succeeded()) {
          promise.complete(transaction);
        } else {
          promise.fail(new HttpStatusException(500, PgExceptionUtil.getMessage(event.cause())));
        }
      });
    return promise.future();
  }

  private Future<Void> updateBudgetTo(Transaction allocation, DBClient client) {
    return budgetService.getBudgetByFundIdAndFiscalYearId(allocation.getFiscalYearId(), allocation.getToFundId(), client)
      .map(budgetTo -> {
        Budget budgetToNew = JsonObject.mapFrom(budgetTo)
          .mapTo(Budget.class);
        CalculationUtils.recalculateBudgetAllocationTo(budgetToNew, allocation, allocation.getAmount());
        budgetService.updateBudgetMetadata(budgetToNew, allocation);
        return budgetToNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(Collections.singletonList(budgetFrom), client))
      .map(i -> null);
  }

  private Future<Void> updateFromBudget(Transaction allocation, DBClient client) {
    if (StringUtils.isEmpty(allocation.getFromFundId())) {
      return Future.succeededFuture();
    }
    return budgetService.getBudgetByFundIdAndFiscalYearId(allocation.getFiscalYearId(), allocation.getFromFundId(), client)
      .map(budgetFrom -> {
        Budget budgetFromNew = JsonObject.mapFrom(budgetFrom)
          .mapTo(Budget.class);
        CalculationUtils.recalculateBudgetAllocationFrom(budgetFromNew, allocation, allocation.getAmount());
        budgetService.updateBudgetMetadata(budgetFromNew, allocation);
        return budgetFromNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(Collections.singletonList(budgetFrom), client))
      .map(i -> null);
  }

  private void handleValidationError(Transaction transfer) {

    List<Error> errors = new ArrayList<>(buildNullValidationError(transfer.getToFundId(), TO_FUND_ID));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
  }

}
