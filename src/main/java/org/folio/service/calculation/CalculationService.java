package org.folio.service.calculation;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.fund.FundService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.Future;

public class CalculationService {

  private final FundService fundService;
  private final LedgerFiscalYearService ledgerFiscalYearService;


  public CalculationService(FundService fundService, LedgerFiscalYearService ledgerFiscalYearService){
    this.fundService = fundService;
    this.ledgerFiscalYearService = ledgerFiscalYearService;
  }

  public void recalculateOverEncumbered(Budget budget, CurrencyUnit currency) {
    double a = subtractMoneyNonNegative(budget.getAllocated(), budget.getExpenditures(), currency);
    a = subtractMoneyNonNegative(a, budget.getAwaitingPayment(), currency);
    double newOverEncumbrance = subtractMoneyNonNegative(budget.getEncumbered(), a, currency);
    budget.setOverEncumbrance(newOverEncumbrance);
  }

  public void recalculateAvailableUnavailable(Budget budget, Double transactionAmount, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoneyNonNegative(budget.getAvailable(), transactionAmount, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
  }

  private Future<Map<LedgerFY, Set<String>>> getGroupedFundIdsByLedgerFy(List<Budget> budgets, DBClient client) {
    return fundService.getFundsByBudgets(budgets, client)
      .compose(funds -> ledgerFiscalYearService.getLedgerFiscalYearsByBudgets(budgets, client)
        .map(ledgerFYears -> groupFundIdsByLedgerFy(ledgerFYears, funds)));
  }

  private Map<LedgerFY, Set<String>> groupFundIdsByLedgerFy(List<LedgerFY> ledgerFYears, List<Fund> funds) {
    Map<String, Set<String>> fundIdsGroupedByLedgerId = funds.stream()
      .collect(groupingBy(Fund::getLedgerId, HashMap::new, Collectors.mapping(Fund::getId, Collectors.toSet())));
    return ledgerFYears.stream()
      .collect(toMap(identity(), ledgerFY -> fundIdsGroupedByLedgerId.get(ledgerFY.getLedgerId())));
  }

  public Future<Void> updateLedgerFYsWithTotals(List<Budget> oldBudgets, List<Budget> newBudgets, DBClient client) {
    return getGroupedFundIdsByLedgerFy(newBudgets, client)
      .map(fundIdsGroupedByLedgerFY -> calculateLedgerFyTotals(fundIdsGroupedByLedgerFY, oldBudgets, newBudgets))
      .compose(ledgersFYears -> ledgerFiscalYearService.updateLedgerFiscalYears(ledgersFYears, client));
  }

  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, Set<String>> fundIdsGroupedByLedgerFY, List<Budget> oldBudgets, List<Budget> newBudgets) {
    String currency = fundIdsGroupedByLedgerFY.keySet().stream().limit(1).map(LedgerFY::getCurrency).findFirst().orElse("USD"); // there always must be at least one ledgerFY record
    Map<String, MonetaryAmount> oldAvailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> oldUnavailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> newAvailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> newUnavailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> availableDifference = getAmountDifference(oldAvailableByFundId, newAvailableByFundId);

    Map<String, MonetaryAmount> unavailableDifference = getAmountDifference(oldUnavailableByFundId, newUnavailableByFundId);
    return calculateLedgerFyTotals(fundIdsGroupedByLedgerFY, availableDifference, unavailableDifference);
  }

  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, Set<String>> groupedLedgerFYs, Map<String, MonetaryAmount> availableDifference, Map<String, MonetaryAmount> unavailableDifference) {
    return groupedLedgerFYs.entrySet().stream().map(ledgerFYListEntry -> updateLedgerFY(ledgerFYListEntry, availableDifference, unavailableDifference)).collect(toList());
  }

  private Map<String, MonetaryAmount> getAmountDifference(Map<String, MonetaryAmount> oldAvailableByFundId, Map<String, MonetaryAmount> newAvailableByFundId) {
    return oldAvailableByFundId.entrySet().stream()
      .map(entry -> {
        MonetaryAmount diff = entry.getValue().subtract(newAvailableByFundId.get(entry.getKey()));
        entry.setValue(diff);
        return entry;
      })
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Collector<Budget, ?, MonetaryAmount> sumAvailable(String currency) {
    return Collectors.mapping(budget -> Money.of(budget.getAvailable(), currency),
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }

  private Collector<Budget, ?, MonetaryAmount> sumUnavailable(String currency) {
    return Collectors.mapping(budget -> Money.of(budget.getUnavailable(), currency),
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }

  private LedgerFY updateLedgerFY(Map.Entry<LedgerFY, Set<String>> ledgerFYListEntry, Map<String, MonetaryAmount> availableDifference, Map<String, MonetaryAmount> unavailableDifference) {
    LedgerFY ledgerFY = ledgerFYListEntry.getKey();

    MonetaryAmount availableAmount = ledgerFYListEntry.getValue().stream()
      .map(availableDifference::get).reduce(MonetaryFunctions::sum)
      .orElse(Money.zero(Monetary.getCurrency(ledgerFY.getCurrency())));

    MonetaryAmount unavailableAmount = ledgerFYListEntry.getValue().stream()
      .map(unavailableDifference::get).reduce(MonetaryFunctions::sum)
      .orElse(Money.zero(Monetary.getCurrency(ledgerFY.getCurrency())));

    double newAvailable = Math.max(Money.of(ledgerFY.getAvailable(), ledgerFY.getCurrency()).subtract(availableAmount).getNumber().doubleValue(), 0d);
    double newUnavailable = Math.max(Money.of(ledgerFY.getUnavailable(), ledgerFY.getCurrency()).subtract(unavailableAmount).getNumber().doubleValue(), 0d);

    return ledgerFY
      .withAvailable(newAvailable)
      .withUnavailable(newUnavailable);
  }

  public void recalculateBudgetTransfer(Budget budgetFromNew, Transaction transfer, Double netTransferAmount) {
    CurrencyUnit currency = Monetary.getCurrency(transfer.getCurrency());

    double newNetTransfers = sumMoney(budgetFromNew.getNetTransfers(), -netTransferAmount, currency);
    budgetFromNew.setNetTransfers(newNetTransfers);

    recalculateOverEncumbered(budgetFromNew, currency);
    recalculateAvailableUnavailable(budgetFromNew, netTransferAmount, currency);

  }
}
