package org.folio.dao.fiscalyearhierarchy;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.FiscalYearHierarchy;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyBudget;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyCollection;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyExpenseClass;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyFund;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyGroup;
import org.folio.rest.persist.DBConn;

public class FiscalYearHierarchyPostgresDAO implements FiscalYearHierarchyDAO {

  public static final String FISCAL_YEAR_HIERARCHY_VIEW = "fiscal_year_hierarchy_view";
  private static final String SELECT_BY_FISCAL_YEAR_ID = "SELECT jsonb FROM %s WHERE jsonb ->> 'fiscalYearId' = $1";

  @Override
  public Future<FiscalYearHierarchyCollection> getFiscalYearHierarchy(DBConn conn, String fiscalYearId) {
    var sql = SELECT_BY_FISCAL_YEAR_ID.formatted(getFullTableName(conn.getTenantId(), FISCAL_YEAR_HIERARCHY_VIEW));
    return conn.execute(sql, Tuple.of(fiscalYearId))
      .map(this::buildHierarchyCollection);
  }

  private FiscalYearHierarchyCollection buildHierarchyCollection(RowSet<Row> rows) {
    Map<String, FiscalYearHierarchy> ledgers = new LinkedHashMap<>();
    Map<String, Map<String, FiscalYearHierarchyGroup>> groupsByLedger = new LinkedHashMap<>();

    for (Row row : rows) {
      JsonObject flatRow = row.getJsonObject("jsonb");
      var ledger = ledgers.computeIfAbsent(flatRow.getString("ledgerId"), id -> toLedgerNode(flatRow));
      var fund = toFundNode(flatRow);
      var groupId = flatRow.getString("groupId");

      if (groupId == null) {
        ledger.getFunds().add(fund);
        continue;
      }
      var group = groupsByLedger.computeIfAbsent(ledger.getLedgerId(), id -> new LinkedHashMap<>())
        .computeIfAbsent(groupId, id -> {
          var groupNode = toGroupNode(flatRow);
          ledger.getGroups().add(groupNode);
          return groupNode;
        });
      group.getFunds().add(fund);
    }

    return new FiscalYearHierarchyCollection()
      .withFiscalYearHierarchies(List.copyOf(ledgers.values()))
      .withTotalRecords(ledgers.size());
  }

  private FiscalYearHierarchy toLedgerNode(JsonObject flatRow) {
    return new FiscalYearHierarchy()
      .withFiscalYearId(flatRow.getString("fiscalYearId"))
      .withFiscalYearCode(flatRow.getString("fiscalYearCode"))
      .withLedgerId(flatRow.getString("ledgerId"))
      .withLedgerCode(flatRow.getString("ledgerCode"))
      .withLedgerName(flatRow.getString("ledgerName"));
  }

  private FiscalYearHierarchyGroup toGroupNode(JsonObject flatRow) {
    return new FiscalYearHierarchyGroup()
      .withGroupId(flatRow.getString("groupId"))
      .withGroupCode(flatRow.getString("groupCode"))
      .withGroupName(flatRow.getString("groupName"));
  }

  private FiscalYearHierarchyFund toFundNode(JsonObject flatRow) {
    return new FiscalYearHierarchyFund()
      .withFundId(flatRow.getString("fundId"))
      .withFundCode(flatRow.getString("fundCode"))
      .withFundName(flatRow.getString("fundName"))
      .withBudget(toBudgetNode(flatRow));
  }

  private FiscalYearHierarchyBudget toBudgetNode(JsonObject flatRow) {
    var budgetId = flatRow.getString("budgetId");
    if (budgetId == null) {
      return null;
    }
    return new FiscalYearHierarchyBudget()
      .withBudgetId(budgetId)
      .withBudgetName(flatRow.getString("budgetName"))
      .withBudgetStatus(flatRow.getString("budgetStatus"))
      .withInitialAllocation(flatRow.getDouble("initialAllocation"))
      .withAllocated(flatRow.getDouble("allocated"))
      .withAvailable(flatRow.getDouble("available"))
      .withBudgetExpenseClasses(toExpenseClasses(flatRow.getJsonArray("budgetExpenseClasses")));
  }

  private List<FiscalYearHierarchyExpenseClass> toExpenseClasses(JsonArray expenseClasses) {
    if (expenseClasses == null) {
      return List.of();
    }
    return expenseClasses.stream()
      .map(o -> ((JsonObject) o).mapTo(FiscalYearHierarchyExpenseClass.class))
      .toList();
  }

}
