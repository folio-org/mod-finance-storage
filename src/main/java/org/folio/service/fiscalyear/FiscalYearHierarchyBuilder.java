package org.folio.service.fiscalyear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.folio.dao.fiscalyear.FiscalYearHierarchyFlatRow;
import org.folio.rest.jaxrs.model.FiscalYearHierarchy;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyBudget;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyExpenseClass;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyFund;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyGroup;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyLedger;

public final class FiscalYearHierarchyBuilder {

  private FiscalYearHierarchyBuilder() {
  }

  public static FiscalYearHierarchy build(String fiscalYearId, List<FiscalYearHierarchyFlatRow> rows) {
    Map<String, LedgerNode> byLedger = new LinkedHashMap<>();
    for (FiscalYearHierarchyFlatRow row : rows) {
      if (row.getLedgerId() == null || row.getFundId() == null || row.getBudgetId() == null) {
        continue;
      }
      LedgerNode ledger = byLedger.computeIfAbsent(row.getLedgerId(),
        id -> new LedgerNode(row.getLedgerId(), row.getLedgerName(), row.getLedgerCode()));
      if (row.getGroupId() != null) {
        GroupNode group = ledger.groups.computeIfAbsent(row.getGroupId(),
          id -> new GroupNode(row.getGroupId(), row.getGroupName(), row.getGroupCode()));
        addBudgetExpense(group.funds, row);
      } else {
        addBudgetExpense(ledger.ungroupedFunds, row);
      }
    }
    List<FiscalYearHierarchyLedger> ledgers = byLedger.values().stream()
      .map(LedgerNode::toModel)
      .collect(Collectors.toList());
    return new FiscalYearHierarchy()
      .withFiscalYearId(fiscalYearId)
      .withLedgers(ledgers);
  }

  private static void addBudgetExpense(Map<String, FundNode> funds, FiscalYearHierarchyFlatRow row) {
    FundNode fund = funds.computeIfAbsent(row.getFundId(),
      id -> new FundNode(row.getFundId(), row.getFundName(), row.getFundCode()));
    BudgetNode budget = fund.budgets.computeIfAbsent(row.getBudgetId(),
      id -> new BudgetNode(row.getBudgetId(), row.getBudgetName()));
    if (row.getExpenseClassId() != null) {
      budget.expenseClasses.putIfAbsent(row.getExpenseClassId(),
        new FiscalYearHierarchyExpenseClass()
          .withId(row.getExpenseClassId())
          .withName(row.getExpenseClassName())
          .withCode(row.getExpenseClassCode()));
    }
  }

  private static final class LedgerNode {
    private final String id;
    private final String name;
    private final String code;
    private final Map<String, GroupNode> groups = new LinkedHashMap<>();
    private final Map<String, FundNode> ungroupedFunds = new LinkedHashMap<>();

    private LedgerNode(String id, String name, String code) {
      this.id = id;
      this.name = name;
      this.code = code;
    }

    private FiscalYearHierarchyLedger toModel() {
      List<FiscalYearHierarchyGroup> groupList = groups.values().stream()
        .map(GroupNode::toModel)
        .collect(Collectors.toList());
      List<FiscalYearHierarchyFund> ungrouped = ungroupedFunds.values().stream()
        .map(FundNode::toModel)
        .collect(Collectors.toList());
      return new FiscalYearHierarchyLedger()
        .withId(id)
        .withName(name)
        .withCode(code)
        .withGroups(groupList)
        .withUngroupedFunds(ungrouped);
    }
  }

  private static final class GroupNode {
    private final String id;
    private final String name;
    private final String code;
    private final Map<String, FundNode> funds = new LinkedHashMap<>();

    private GroupNode(String id, String name, String code) {
      this.id = id;
      this.name = name;
      this.code = code;
    }

    private FiscalYearHierarchyGroup toModel() {
      List<FiscalYearHierarchyFund> fundList = funds.values().stream()
        .map(FundNode::toModel)
        .collect(Collectors.toList());
      return new FiscalYearHierarchyGroup()
        .withId(id)
        .withName(name)
        .withCode(code)
        .withFunds(fundList);
    }
  }

  private static final class FundNode {
    private final String id;
    private final String name;
    private final String code;
    private final Map<String, BudgetNode> budgets = new LinkedHashMap<>();

    private FundNode(String id, String name, String code) {
      this.id = id;
      this.name = name;
      this.code = code;
    }

    private FiscalYearHierarchyFund toModel() {
      List<FiscalYearHierarchyBudget> budgetList = budgets.values().stream()
        .map(BudgetNode::toModel)
        .collect(Collectors.toList());
      return new FiscalYearHierarchyFund()
        .withId(id)
        .withName(name)
        .withCode(code)
        .withBudgets(budgetList);
    }
  }

  private static final class BudgetNode {
    private final String id;
    private final String name;
    private final Map<String, FiscalYearHierarchyExpenseClass> expenseClasses = new LinkedHashMap<>();

    private BudgetNode(String id, String name) {
      this.id = id;
      this.name = name;
    }

    private FiscalYearHierarchyBudget toModel() {
      return new FiscalYearHierarchyBudget()
        .withId(id)
        .withName(name)
        .withExpenseClasses(new ArrayList<>(expenseClasses.values()));
    }
  }
}
