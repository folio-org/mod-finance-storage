package org.folio.service.transactions;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.persist.HelperUtils.buildNullValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.folio.utils.CalculationUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;

public class TransferService extends AbstractTransactionService implements TransactionManagingStrategy {

  private static final Logger logger = LogManager.getLogger(TransferService.class);

  private final BudgetService budgetService;

  public TransferService(BudgetService budgetService, TransactionDAO transactionDAO) {
    super(transactionDAO);
    this.budgetService = budgetService;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transfer, DBConn conn) {
    try {
      handleValidationError(transfer);
    } catch (HttpException e) {
      return Future.failedFuture(e);
    }
    return super.createTransaction(transfer, conn)
      .compose(createdTransfer -> {
        if (createdTransfer.getFromFundId() != null) {
          return updateBudgetsTransferFrom(createdTransfer, conn);
        }
        return Future.succeededFuture(createdTransfer);
      })
      .compose(createdTransfer -> updateBudgetsTransferTo(createdTransfer, conn))
      .onSuccess(v -> logger.info("createTransaction:: Transfer with id {} and associated data were successfully processed",
        transfer.getId()))
      .onFailure(e -> logger.error("createTransaction:: Transfer with id {} or associated data failed to be processed",
        transfer.getId(), e));
  }

  @Override
  public Transaction.TransactionType getStrategyName() {
    return Transaction.TransactionType.TRANSFER;
  }

  private Future<Transaction> updateBudgetsTransferFrom(Transaction transfer, DBConn conn) {
    return budgetService.getBudgetByFiscalYearIdAndFundIdForUpdate(transfer.getFiscalYearId(), transfer.getFromFundId(), conn)
      .map(budgetFromOld -> {
        Budget budgetFromNew = JsonObject.mapFrom(budgetFromOld).mapTo(Budget.class);
        CalculationUtils.recalculateBudgetTransfer(budgetFromNew, transfer, transfer.getAmount());
        budgetService.updateBudgetMetadata(budgetFromNew, transfer);
        return budgetFromNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(Collections.singletonList(budgetFrom), conn))
      .map(v -> transfer);
  }

  private Future<Transaction> updateBudgetsTransferTo(Transaction transfer, DBConn conn) {
    return budgetService.getBudgetByFiscalYearIdAndFundIdForUpdate(transfer.getFiscalYearId(), transfer.getToFundId(), conn)
      .map(budgetTo -> {
        Budget budgetToNew = JsonObject.mapFrom(budgetTo).mapTo(Budget.class);
        CalculationUtils.recalculateBudgetTransfer(budgetToNew, transfer, -transfer.getAmount());
        budgetService.updateBudgetMetadata(budgetToNew, transfer);
        return budgetToNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(Collections.singletonList(budgetFrom), conn))
      .map(v -> transfer);
  }

  private void handleValidationError(Transaction transfer) {

    List<Error> errors = new ArrayList<>(buildNullValidationError(transfer.getToFundId(), TO_FUND_ID));

    if (isNotEmpty(errors)) {
      logger.error("handleValidationError: Validation error for transfer with toFundId {}", transfer.getToFundId());
      throw new HttpException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
  }
}
