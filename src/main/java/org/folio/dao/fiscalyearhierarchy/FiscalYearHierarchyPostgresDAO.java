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
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.FiscalYearHierarchy;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyBudget;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyCollection;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyExpenseClass;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyFund;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyGroup;
import org.folio.rest.persist.DBConn;

public class FiscalYearHierarchyPostgresDAO implements FiscalYearHierarchyDAO {

  public static final String FISCAL_YEAR_HIERARCHY_VIEW = "fiscal_year_hierarchy_view";

  @Override
  public Future<FiscalYearHierarchyCollection> getFiscalYearHierarchy(DBConn conn, FiscalYearHierarchyFilter filter) {
    var tableName = getFullTableName(conn.getTenantId(), FISCAL_YEAR_HIERARCHY_VIEW);
    var sql = new StringBuilder("SELECT jsonb FROM ").append(tableName).append(" WHERE jsonb ->> 'fiscalYearId' = $1");
    var params = Tuple.of(filter.fiscalYearId());

    appendStatusFilter(sql, params, "ledgerStatus", filter.ledgerStatus());
    appendStatusFilter(sql, params, "groupStatus", filter.groupStatus());
    appendStatusFilter(sql, params, "fundStatus", filter.fundStatus());
    appendStatusFilter(sql, params, "budgetStatus", filter.budgetStatus());

    return conn.execute(sql.toString(), params)
      .map(rows -> buildHierarchyCollection(rows, filter.expenseClassStatus()));
  }

  private void appendStatusFilter(StringBuilder sql, Tuple params, String jsonbField, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    params.addValue(value);
    sql.append(" AND jsonb ->> '").append(jsonbField).append("' = $").append(params.size());
  }

  private FiscalYearHierarchyCollection buildHierarchyCollection(RowSet<Row> rows, String expenseClassStatus) {
    Map<String, FiscalYearHierarchy> ledgers = new LinkedHashMap<>();
    Map<String, Map<String, FiscalYearHierarchyGroup>> groupsByLedger = new LinkedHashMap<>();

    for (Row row : rows) {
      JsonObject flatRow = row.getJsonObject("jsonb");
      var ledger = ledgers.computeIfAbsent(flatRow.getString("ledgerId"), id -> toLedgerNode(flatRow));
      var fund = toFundNode(flatRow, expenseClassStatus);
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

  private FiscalYearHierarchyFund toFundNode(JsonObject flatRow, String expenseClassStatus) {
    return new FiscalYearHierarchyFund()
      .withFundId(flatRow.getString("fundId"))
      .withFundCode(flatRow.getString("fundCode"))
      .withFundName(flatRow.getString("fundName"))
      .withBudget(toBudgetNode(flatRow, expenseClassStatus));
  }

  private FiscalYearHierarchyBudget toBudgetNode(JsonObject flatRow, String expenseClassStatus) {
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
      .withBudgetExpenseClasses(toExpenseClasses(flatRow.getJsonArray("budgetExpenseClasses"), expenseClassStatus));
  }

  private List<FiscalYearHierarchyExpenseClass> toExpenseClasses(JsonArray expenseClasses, String expenseClassStatus) {
    if (expenseClasses == null) {
      return List.of();
    }
    return expenseClasses.stream()
      .map(o -> ((JsonObject) o).mapTo(FiscalYearHierarchyExpenseClass.class))
      .filter(ec -> StringUtils.isBlank(expenseClassStatus) || expenseClassStatus.equals(ec.getStatus()))
      .toList();
  }

}
