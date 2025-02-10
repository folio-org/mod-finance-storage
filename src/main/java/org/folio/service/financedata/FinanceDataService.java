package org.folio.service.financedata;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
import org.folio.rest.util.ErrorCodes;
import org.folio.service.budget.BudgetService;
import org.folio.service.fiscalyear.FiscalYearService;
import org.folio.service.fund.FundService;
import org.folio.service.transactions.batch.BatchTransactionService;

public class FinanceDataService {
  private static final Logger logger = LogManager.getLogger();

  private final FundService fundService;
  private final BudgetService budgetService;
  private final FiscalYearService fiscalYearService;
  private final BatchTransactionService batchTransactionService;

  public FinanceDataService(FundService fundService, BudgetService budgetService, FiscalYearService fiscalYearService,
      BatchTransactionService batchTransactionService) {
    this.fundService = fundService;
    this.budgetService = budgetService;
    this.fiscalYearService = fiscalYearService;
    this.batchTransactionService = batchTransactionService;
  }

  public Future<Void> update(FyFinanceDataCollection entity, RequestContext requestContext) {
    if (CollectionUtils.isEmpty(entity.getFyFinanceData())) {
      return Future.succeededFuture();
    }
    var dbClient = requestContext.toDBClient();
    return dbClient
      .withTrans(conn -> {
        var updateFundFuture = processFundUpdate(entity, conn);
        var updateBudgetFuture = processBudgetUpdate(entity, conn);
        return GenericCompositeFuture.all(List.of(updateFundFuture, updateBudgetFuture))
          .compose(v -> processAllocationTransaction(entity, conn, requestContext.getHeaders()));
      })
      .onSuccess(v -> logger.info("Successfully updated finance data"))
      .onFailure(e -> logger.error("Failed to update finance data", e))
      .mapEmpty();
  }

  private Future<Void> processFundUpdate(FyFinanceDataCollection entity, DBConn conn) {
    List<String> fundIds = entity.getFyFinanceData().stream()
      .map(FyFinanceData::getFundId)
      .toList();
    return fundService.getFundsByIds(fundIds, conn)
      .map(funds -> setNewValuesForFunds(funds, entity))
      .compose(funds -> fundService.updateFunds(funds, conn))
      .recover(t -> Future.failedFuture(new HttpException(500, ErrorCodes.FAILED_TO_UPDATE_FUNDS, t)));
  }

  private Future<Void> processBudgetUpdate(FyFinanceDataCollection entity, DBConn conn) {
    List<String> budgetIds = entity.getFyFinanceData().stream()
      .map(FyFinanceData::getBudgetId)
      .toList();
    return budgetService.getBudgetsByIds(budgetIds, conn)
      .map(budgets -> setNewValuesForBudgets(budgets, entity))
      .compose(budgets -> budgetService.updateBatchBudgets(budgets, conn, false))
      .recover(t -> Future.failedFuture(new HttpException(500, ErrorCodes.FAILED_TO_UPDATE_BUDGETS, t)));
  }

  private List<Fund> setNewValuesForFunds(List<Fund> funds, FyFinanceDataCollection entity) {
    return funds.stream()
      .map(fund -> setNewValues(fund, entity))
      .toList();
  }

  private Fund setNewValues(Fund fund, FyFinanceDataCollection entity) {
    var fundFinanceData = entity.getFyFinanceData().stream()
      .filter(data -> data.getFundId().equals(fund.getId()))
      .findFirst()
      .orElseThrow();

    Fund.FundStatus oldStatus = fund.getFundStatus();
    Fund.FundStatus newStatus = Fund.FundStatus.fromValue(fundFinanceData.getFundStatus().value());
    if (oldStatus.equals(newStatus)) {
      boolean positiveAllocation = fundFinanceData.getBudgetAllocationChange() != null &&
        fundFinanceData.getBudgetAllocationChange() > 0;
      boolean inactiveFund = Fund.FundStatus.FROZEN.equals(oldStatus) || Fund.FundStatus.INACTIVE.equals(oldStatus);
      if (positiveAllocation && inactiveFund) {
        newStatus = Fund.FundStatus.ACTIVE;
      }
    }
    fund.setFundStatus(newStatus);
    fund.setDescription(fundFinanceData.getFundDescription());
    if (fundFinanceData.getFundTags() != null && isNotEmpty(fundFinanceData.getFundTags().getTagList())) {
      fund.setTags(new Tags().withTagList(fundFinanceData.getFundTags().getTagList()));
    }
    return fund;
  }

  private List<Budget> setNewValuesForBudgets(List<Budget> budgets, FyFinanceDataCollection entity) {
    return budgets.stream()
      .map(budget -> setNewValues(budget, entity))
      .toList();
  }

  private Budget setNewValues(Budget budget, FyFinanceDataCollection entity) {
    var budgetFinanceData = entity.getFyFinanceData().stream()
      .filter(data -> data.getBudgetId().equals(budget.getId()))
      .findFirst()
      .orElseThrow();

    return budget.withBudgetStatus(Budget.BudgetStatus.fromValue(budgetFinanceData.getBudgetStatus().value()))
      .withAllowableExpenditure(budgetFinanceData.getBudgetAllowableExpenditure())
      .withAllowableEncumbrance(budgetFinanceData.getBudgetAllowableEncumbrance());
  }

  private Future<Void> processAllocationTransaction(FyFinanceDataCollection fyFinanceDataCollection, DBConn conn,
      Map<String, String> okapiHeaders) {
    if (fyFinanceDataCollection.getFyFinanceData().stream()
        .noneMatch(data -> data.getBudgetAllocationChange() != null && data.getBudgetAllocationChange() != 0.)) {
      return Future.succeededFuture();
    }
    return fiscalYearService.getFiscalYearById(getFiscalYearId(fyFinanceDataCollection), conn)
      .map(fiscalYear -> createAllocationTransactions(fyFinanceDataCollection, fiscalYear.getCurrency()))
      .compose(transactions -> createBatchTransaction(transactions, conn, okapiHeaders))
      .recover(t -> Future.failedFuture(new HttpException(500, ErrorCodes.FAILED_TO_CREATE_ALLOCATIONS, t)));
  }

  private String getFiscalYearId(FyFinanceDataCollection fyFinanceDataCollection) {
    return fyFinanceDataCollection.getFyFinanceData().get(0).getFiscalYearId();
  }

  private List<Transaction> createAllocationTransactions(FyFinanceDataCollection financeDataCollection, String currency) {
    return financeDataCollection.getFyFinanceData().stream()
      .filter(financeData -> financeData.getBudgetAllocationChange() != null && financeData.getBudgetAllocationChange() != 0.)
      .map(financeData -> createAllocationTransaction(financeData, currency))
      .toList();
  }

  private Transaction createAllocationTransaction(FyFinanceData financeData, String currency) {
    var allocationChange = financeData.getBudgetAllocationChange();
    logger.info("createAllocationTransaction:: Creating allocation transaction for fund '{}' and budget '{}' with allocation '{}'",
      financeData.getFundId(), financeData.getBudgetId(), allocationChange);

    var transaction = new Transaction()
      .withTransactionType(Transaction.TransactionType.ALLOCATION)
      .withId(UUID.randomUUID().toString())
      .withAmount(Math.abs(allocationChange))
      .withFiscalYearId(financeData.getFiscalYearId())
      .withSource(Transaction.Source.USER)
      .withCurrency(currency);

    // For negative allocation (decrease), use fromFundId
    // For positive allocation (increase), use toFundId
    if (allocationChange > 0) {
      transaction.withToFundId(financeData.getFundId());
    } else {
      transaction.withFromFundId(financeData.getFundId());
    }

    return transaction;
  }

  private Future<Void> createBatchTransaction(List<Transaction> transactions, DBConn conn, Map<String, String> okapiHeaders) {
    Batch batch = new Batch().withTransactionsToCreate(transactions);
    return batchTransactionService.processBatch(batch, conn, okapiHeaders);
  }

}
