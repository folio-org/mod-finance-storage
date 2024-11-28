package org.folio.service.financedata;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.List;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;

public class FinanceDataService {
  private static final Logger logger = LogManager.getLogger();

  private final FundService fundService;
  private final BudgetService budgetService;

  public FinanceDataService(FundService fundService, BudgetService budgetService) {
    this.fundService = fundService;
    this.budgetService = budgetService;
  }

  public Future<Void> update(FyFinanceDataCollection entity, RequestContext requestContext) {
    var dbClient = requestContext.toDBClient();
    return dbClient
      .withTrans(conn -> {
        var updateFundFuture = processFundUpdate(entity, conn);
        var updateBudgetFuture = processBudgetUpdate(entity, conn);
        return GenericCompositeFuture.all(List.of(updateFundFuture, updateBudgetFuture));
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
      .compose(funds -> {
        var futures = funds.stream()
          .map(fund -> setNewValues(fund, entity))
          .map(fund -> fundService.updateFundWithMinChange(fund, conn))
          .toList();
        return GenericCompositeFuture.all(futures).mapEmpty();
      });
  }

  private Future<Void> processBudgetUpdate(FyFinanceDataCollection entity, DBConn conn) {
    List<String> budgetIds = entity.getFyFinanceData().stream()
      .map(FyFinanceData::getBudgetId)
      .toList();
    return budgetService.getBudgetsByIds(budgetIds, conn)
      .map(budgets -> setNewValues(budgets, entity))
      .compose(budgets -> budgetService.updateBatchBudgets(budgets, conn));
  }

  private Fund setNewValues(Fund fund, FyFinanceDataCollection entity) {
    var fundFinanceData = entity.getFyFinanceData().stream()
      .filter(data -> data.getFundId().equals(fund.getId()))
      .findFirst()
      .orElseThrow();

    fund.setDescription(fundFinanceData.getFundDescription());
    if (fundFinanceData.getFundTags() != null && isNotEmpty(fundFinanceData.getFundTags().getTagList())) {
      fund.setTags(new Tags().withTagList(fundFinanceData.getFundTags().getTagList()));
    }
    return fund;
  }

  private List<Budget> setNewValues(List<Budget> budgets, FyFinanceDataCollection entity) {
    return budgets.stream()
      .map(budget -> setNewValues(budget, entity))
      .toList();
  }

  private Budget setNewValues(Budget budget, FyFinanceDataCollection entity) {
    var budgetFinanceData = entity.getFyFinanceData().stream()
      .filter(data -> data.getFundId().equals(budget.getId()))
      .findFirst()
      .orElseThrow();

    return budget.withName(budgetFinanceData.getBudgetName())
      .withBudgetStatus(Budget.BudgetStatus.fromValue(budgetFinanceData.getBudgetStatus().value()))
      .withInitialAllocation(budgetFinanceData.getBudgetInitialAllocation())
      .withAllocated(budgetFinanceData.getBudgetCurrentAllocation())
      .withAllowableEncumbrance(budgetFinanceData.getBudgetAllowableExpenditure())
      .withAllowableEncumbrance(budgetFinanceData.getBudgetAllowableEncumbrance())
      .withAcqUnitIds(budgetFinanceData.getBudgetAcqUnitIds());
  }
}
