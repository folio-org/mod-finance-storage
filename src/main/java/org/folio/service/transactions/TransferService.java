package org.folio.service.transactions;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.persist.HelperUtils.buildNullValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.service.budget.BudgetService;
import org.folio.service.calculation.CalculationService;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class TransferService extends AbstractTransactionService {

  private final BudgetService budgetService;
  private final CalculationService calculationService;

  public TransferService(BudgetService budgetService, CalculationService calculationService) {
    this.budgetService = budgetService;
    this.calculationService = calculationService;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transfer, Context context, Map<String, String> okapiHeaders) {
    Promise<Transaction> promise = Promise.promise();

    try {
      handleValidationError(transfer);
    } catch (HttpStatusException e) {
      return Future.failedFuture(e);
    }

    DBClient client = new DBClient(context, okapiHeaders);
    client.startTx()
      .compose(v -> createTransfer(transfer, client)
        .compose(createdTransfer -> {
        if (transfer.getFromFundId() != null) {
          return updateBudgetsTransferFrom(client, transfer);
        }
        return Future.succeededFuture();
      })
        .compose(toBudget -> updateBudgetsTransferTo(client, transfer))
        .compose(ok -> client.endTx())
        .onComplete(result -> {
          if (result.failed()) {
            log.error("Transfer or associated data failed to be processed", result.cause());
            client.rollbackTransaction();
          } else {
            promise.complete(transfer);
            log.info("Transactions and associated data were successfully processed");
          }
        }));
    return promise.future();
  }

  public Future<Transaction> createTransfer(Transaction transaction, DBClient dbClient) {
    Promise<Transaction> promise = Promise.promise();
    dbClient.getPgClient()
      .save(dbClient.getConnection(), TRANSACTION_TABLE, transaction, event -> {
        if (event.succeeded()) {
          transaction.setId(event.result());
          promise.complete(transaction);
        } else {
          promise.fail(new HttpStatusException(500, PgExceptionUtil.getMessage(event.cause())));
        }
      });
    return promise.future();
  }

  private Future<Void> updateBudgetsTransferFrom(DBClient dbClient, Transaction transfer) {
    return budgetService.getBudgetByFundIdAndFiscalYearId(transfer.getFiscalYearId(), transfer.getFromFundId(), dbClient)
      .map(budgetFromOld -> {
        Budget budgetFromNew = JsonObject.mapFrom(budgetFromOld).mapTo(Budget.class);

        calculationService.recalculateBudgetTransfer(budgetFromNew, transfer, transfer.getAmount());
        calculationService.updateLedgerFYsWithTotals(Collections.singletonList(budgetFromOld),
            Collections.singletonList(budgetFromNew), dbClient);
        return budgetFromNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(Collections.singletonList(budgetFrom), dbClient))
      .map(i -> null);
  }

  private Future<Void> updateBudgetsTransferTo(DBClient dbClient, Transaction transfer) {
    return budgetService.getBudgetByFundIdAndFiscalYearId(transfer.getFiscalYearId(), transfer.getToFundId(), dbClient)
      .map(budgetTo -> {
        Budget budgetToNew = JsonObject.mapFrom(budgetTo).mapTo(Budget.class);
        calculationService.recalculateBudgetTransfer(budgetToNew, transfer, -transfer.getAmount());
        calculationService.updateLedgerFYsWithTotals(Collections.singletonList(budgetTo), Collections.singletonList(budgetToNew), dbClient);
        budgetService.updateBudgetMetadata(budgetToNew, transfer);

        return budgetToNew;
      })
      .compose(budgetFrom -> budgetService.updateBatchBudgets(Collections.singletonList(budgetFrom), dbClient))
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
