package org.folio.service.transactions;

import static java.util.Collections.singletonList;
import static org.folio.rest.util.ErrorCodes.MISSING_FUND_ID;
import static org.folio.rest.util.ErrorCodes.MUST_BE_POSITIVE;

import java.util.List;
import java.util.UUID;

import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.folio.utils.CalculationUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class AllocationService extends DefaultTransactionService implements TransactionManagingStrategy {

  private static final Logger logger = LogManager.getLogger(AllocationService.class);

  private final BudgetService budgetService;

  public AllocationService(BudgetService budgetService, TransactionDAO transactionDAO) {
    super(transactionDAO);
    this.budgetService = budgetService;
  }

  @Override
  public Transaction.TransactionType getStrategyName() {
    return Transaction.TransactionType.ALLOCATION;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction allocation, DBConn conn) {
    try {
      handleValidationError(allocation);
    } catch (HttpException e) {
      return Future.failedFuture(e);
    }
    return budgetService.checkBudgetHaveMoneyForTransaction(allocation, conn)
      .compose(v -> createAllocation(allocation, conn))
      .compose(v -> updateFromBudget(allocation, conn))
      .compose(v -> updateBudgetTo(allocation, conn))
      .map(v -> allocation)
      .onSuccess(v -> logger.info("createTransaction:: Allocation with id {} and associated data were successfully processed",
        allocation.getId()))
      .onFailure(e -> logger.error("createTransaction:: Allocation with id {} or associated data failed to be processed",
        allocation.getId(), e));
  }

  private Future<Transaction> createAllocation(Transaction transaction, DBConn conn) {
    logger.debug("createAllocation:: Trying to created allocation");
    if (StringUtils.isEmpty(transaction.getId())) {
      transaction.setId(UUID.randomUUID().toString());
    }
    return conn.save(TRANSACTION_TABLE, transaction.getId(), transaction)
      .onSuccess(s -> logger.info("createAllocation:: Allocation with id {} successfully created", transaction.getId()))
      .onFailure(e -> logger.error("createAllocation:: Creating the allocation with id {} failed", transaction.getId(), e))
      .map(s -> transaction);
  }

  private Future<Void> updateBudgetTo(Transaction allocation, DBConn conn) {
    if (StringUtils.isEmpty(allocation.getToFundId())) {
      return Future.succeededFuture();
    }
    return budgetService.getBudgetByFiscalYearIdAndFundIdForUpdate(allocation.getFiscalYearId(), allocation.getToFundId(), conn)
      .map(budgetTo -> {
        Budget budgetToNew = JsonObject.mapFrom(budgetTo)
          .mapTo(Budget.class);
        CalculationUtils.recalculateBudgetAllocationTo(budgetToNew, allocation, allocation.getAmount());
        budgetService.updateBudgetMetadata(budgetToNew, allocation);
        return budgetToNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(singletonList(budgetFrom), conn))
      .map(i -> null);
  }

  private Future<Void> updateFromBudget(Transaction allocation, DBConn conn) {
    if (StringUtils.isEmpty(allocation.getFromFundId())) {
      return Future.succeededFuture();
    }
    return budgetService.getBudgetByFiscalYearIdAndFundIdForUpdate(allocation.getFiscalYearId(), allocation.getFromFundId(), conn)
      .map(budgetFrom -> {
        Budget budgetFromNew = JsonObject.mapFrom(budgetFrom).mapTo(Budget.class);
        CalculationUtils.recalculateBudgetAllocationFrom(budgetFromNew, allocation, allocation.getAmount());
        budgetService.updateBudgetMetadata(budgetFromNew, allocation);
        return budgetFromNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(singletonList(budgetFrom), conn))
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
