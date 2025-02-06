package org.folio.service.financedata;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.List;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
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
    if (CollectionUtils.isEmpty(entity.getFyFinanceData())) {
      return Future.succeededFuture();
    }
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
      .map(funds -> setNewValuesForFunds(funds, entity))
      .compose(funds -> fundService.updateFunds(funds, conn));
  }

  private Future<Void> processBudgetUpdate(FyFinanceDataCollection entity, DBConn conn) {
    List<String> budgetIds = entity.getFyFinanceData().stream()
      .map(FyFinanceData::getBudgetId)
      .toList();
    return budgetService.getBudgetsByIds(budgetIds, conn)
      .map(budgets -> setNewValuesForBudgets(budgets, entity))
      .compose(budgets -> budgetService.updateBatchBudgets(budgets, conn, false));
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

    fund.setDescription(fundFinanceData.getFundDescription());
    fund.setFundStatus(Fund.FundStatus.fromValue(fundFinanceData.getFundStatus().value()));
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
}
