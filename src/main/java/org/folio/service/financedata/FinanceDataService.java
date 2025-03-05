package org.folio.service.financedata;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.model.Metadata;
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

  public Future<FyFinanceDataCollection> update(FyFinanceDataCollection entity, RequestContext requestContext) {
    if (CollectionUtils.isEmpty(entity.getFyFinanceData())) {
      return succeededFuture();
    }
    var dbClient = requestContext.toDBClient();
    Map<String, String> okapiHeaders = requestContext.getHeaders();
    return dbClient
      .withTrans(conn -> fiscalYearService.getFiscalYearById(getFiscalYearId(entity), conn)
        .compose(fiscalYear -> createBudgetsIfNeeded(entity, fiscalYear, conn, okapiHeaders)
          .compose(v -> updateFundAndBudget(entity, conn))
          .compose(v -> processAllocationTransaction(entity, fiscalYear, conn, okapiHeaders))))
      .onSuccess(v -> logger.info("update:: Successfully updated finance data"))
      .onFailure(e -> logger.error("Failed to update finance data", e))
      .map(v -> entity);
  }

  private Future<Void> createBudgetsIfNeeded(FyFinanceDataCollection financeDataCollection, FiscalYear fiscalYear, DBConn conn,
                                             Map<String, String> okapiHeaders) {
    // Skip creating budgets for past fiscal years
    if (isFiscalYearPast(fiscalYear)) {
      return succeededFuture();
    }

    var newBudgets = financeDataCollection.getFyFinanceData().stream()
      .filter(this::needsBudgetCreation)
      .map(financeData -> createAndLinkBudget(financeData, fiscalYear, okapiHeaders))
      .toList();

    if (newBudgets.isEmpty()) {
      logger.info("createBudgetsIfNeeded:: No need to create budgets for fiscalYear: {}", fiscalYear.getId());
      return succeededFuture();
    }

    logger.info("createBudgetsIfNeeded:: Creating {} budget(s) for fiscalYear: {}", newBudgets.size(), fiscalYear.getId());
    return budgetService.createBatchBudgets(newBudgets, conn);
  }

  private boolean isFiscalYearPast(FiscalYear fiscalYear) {
    Date now = new Date();
    return fiscalYear.getPeriodEnd().before(now);
  }

  private boolean needsBudgetCreation(FyFinanceData financeData) {
    return financeData.getBudgetId() == null
      && (financeData.getBudgetStatus() != null || requireNonNullElse(financeData.getBudgetAllocationChange(), 0.0) > 0);
  }

  private Budget createAndLinkBudget(FyFinanceData financeData, FiscalYear fiscalYear, Map<String, String> okapiHeaders) {
    var budgetId = UUID.randomUUID().toString();
    var budgetStatus = newBudgetStatus(fiscalYear);

    var newBudget = new Budget()
      .withId(budgetId)
      .withName(requireNonNullElse(financeData.getBudgetName(), financeData.getFundCode() + '-' + financeData.getFiscalYearCode()))
      .withBudgetStatus(budgetStatus)
      .withAllocated(0.0)
      .withFundId(financeData.getFundId())
      .withFiscalYearId(financeData.getFiscalYearId());

    populateBudgetMetadata(newBudget, okapiHeaders);

    financeData.setBudgetName(newBudget.getName());
    financeData.setBudgetId(budgetId);
    // avoid overriding existing budget status, existing status will be used in updating budget process
    if (financeData.getBudgetStatus() == null) {
      financeData.setBudgetStatus(budgetStatus.value());
    }
    return newBudget;
  }

  private Budget.BudgetStatus newBudgetStatus(FiscalYear fiscalYear) {
    Date now = new Date();
    Budget.BudgetStatus budgetStatus;
    if (fiscalYear.getPeriodStart().before(now)) {
      budgetStatus = Budget.BudgetStatus.ACTIVE;
    } else {
      budgetStatus = Budget.BudgetStatus.PLANNED;
    }
    return budgetStatus;
  }

  private void populateBudgetMetadata(Budget budget, Map<String, String> okapiHeaders) {
    String userId = okapiHeaders.get(XOkapiHeaders.USER_ID);
    if (userId == null) {
      try {
        userId = (new OkapiToken(okapiHeaders.get(XOkapiHeaders.TOKEN))).getUserIdWithoutValidation();
      } catch (Exception ignored) {
        // could not find user id - ignoring
      }
    }
    Metadata md = new Metadata();
    md.setUpdatedDate(new Date());
    md.setCreatedDate(md.getUpdatedDate());
    md.setCreatedByUserId(userId);
    md.setUpdatedByUserId(userId);
    budget.setMetadata(md);
  }

  private Future<Void> updateFundAndBudget(FyFinanceDataCollection entity, DBConn conn) {
    var updateFundFuture = processFundUpdate(entity, conn);
    var updateBudgetFuture = processBudgetUpdate(entity, conn);
    return GenericCompositeFuture.all(List.of(updateFundFuture, updateBudgetFuture))
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
      .filter(Objects::nonNull)
      .toList();
    if (budgetIds.isEmpty()) {
      return succeededFuture();
    }
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

    var oldStatus = fund.getFundStatus();
    var newStatus = Fund.FundStatus.fromValue(fundFinanceData.getFundStatus());
    if (oldStatus.equals(newStatus)) {
      boolean positiveAllocation = fundFinanceData.getBudgetAllocationChange() != null &&
        fundFinanceData.getBudgetAllocationChange() > 0;
      boolean inactiveFund = Fund.FundStatus.FROZEN.equals(oldStatus) || Fund.FundStatus.INACTIVE.equals(oldStatus);
      if (positiveAllocation && inactiveFund) {
        newStatus = Fund.FundStatus.ACTIVE;
      }
    }
    fund.setFundStatus(newStatus);
    fundFinanceData.setFundStatus(newStatus.value());
    if (StringUtils.isNotEmpty(fundFinanceData.getFundDescription())) {
      fund.setDescription(fundFinanceData.getFundDescription());
    }
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

    return budget.withBudgetStatus(Budget.BudgetStatus.fromValue(budgetFinanceData.getBudgetStatus()))
      .withAllowableExpenditure(budgetFinanceData.getBudgetAllowableExpenditure())
      .withAllowableEncumbrance(budgetFinanceData.getBudgetAllowableEncumbrance());
  }

  private Future<Void> processAllocationTransaction(FyFinanceDataCollection fyFinanceDataCollection, FiscalYear fiscalYear,
                                                    DBConn conn, Map<String, String> okapiHeaders) {
    if (fyFinanceDataCollection.getFyFinanceData().stream()
      .noneMatch(data -> data.getBudgetAllocationChange() != null && data.getBudgetAllocationChange() != 0.)) {
      return succeededFuture();
    }
    List<Transaction> transactions = createAllocationTransactions(fyFinanceDataCollection, fiscalYear.getCurrency());
    return createBatchTransaction(transactions, conn, okapiHeaders)
      .recover(t -> Future.failedFuture(new HttpException(500, ErrorCodes.FAILED_TO_CREATE_ALLOCATIONS, t)));
  }

  private String getFiscalYearId(FyFinanceDataCollection fyFinanceDataCollection) {
    return fyFinanceDataCollection.getFyFinanceData().getFirst().getFiscalYearId();
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
      .withCurrency(currency)
      .withDescription(financeData.getTransactionDescription());

    if (financeData.getTransactionTag() != null && isNotEmpty(financeData.getTransactionTag().getTagList())) {
      transaction.setTags(new Tags().withTagList(financeData.getTransactionTag().getTagList()));
    }

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
