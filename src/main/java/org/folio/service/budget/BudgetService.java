package org.folio.service.budget;

import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ErrorCodes.TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.budget.BudgetDAO;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.folio.service.group.GroupService;
import org.folio.utils.CalculationUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.exception.HttpException;
import io.vertx.sqlclient.Tuple;

public class BudgetService {

  private static final Logger logger = LogManager.getLogger(BudgetService.class);

  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";

  private final DBClientFactory dbClientFactory;
  private final GroupService groupService;
  private final BudgetDAO budgetDAO;

  public BudgetService(DBClientFactory dbClientFactory, BudgetDAO budgetDAO, GroupService groupService) {
    this.dbClientFactory = dbClientFactory;
    this.budgetDAO = budgetDAO;
    this.groupService = groupService;
  }

  public Future<Budget> createBudget(Budget entity, RequestContext requestContext) {
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return client.withTrans(conn -> budgetDAO.createBudget(entity, conn)
      .compose(budget -> groupService.updateBudgetIdForGroupFundFiscalYears(budget, conn)
        .map(v -> budget))
    );
  }

  public void deleteById(String id, Context vertxContext, Map<String, String> headers,
      Handler<AsyncResult<Response>> asyncResultHandler) {
    logger.debug("deleteById:: Trying to delete finance storage budgets with id {}", id);
    vertxContext.runOnContext(v -> {
      DBClient client = new DBClient(vertxContext, headers);
      client.withTrans(conn -> budgetDAO.getBudgetById(id, conn)
        .compose(budget -> checkTransactions(budget, conn).map(budget)
          .compose(t -> unlinkGroupFundFiscalYears(id, conn))
          .compose(t -> budgetDAO.deleteBudget(id, conn))
          .compose(t -> deleteAllocationTransactions(budget, conn))
          .onFailure(e -> logger.error("deleteById:: Deleting finance storage budgets with id {} failed", id, e))
        )
      ).onComplete(handleNoContentResponse(asyncResultHandler, id, "deleteById:: Budget {} {} deleted"));
    });
  }

  public Future<Void> createBatchBudgets(List<Budget> budgets, DBConn conn) {
    return budgetDAO.createBatchBudgets(budgets, conn)
      .compose(v -> {
        var futures = budgets.stream()
          .map(budget -> groupService.updateBudgetIdForGroupFundFiscalYears(budget, conn))
          .toList();
        return GenericCompositeFuture.join(futures)
          .mapEmpty();
      });
  }

  public Future<Void> updateBatchBudgets(List<Budget> budgets, DBConn conn, boolean clearReadOnlyFields) {
    if (Boolean.TRUE.equals(clearReadOnlyFields)) {
      budgets.forEach(this::clearReadOnlyFields);
    }
    return budgetDAO.updateBatchBudgets(budgets, conn);
  }

  public Future<List<Budget>> getBudgets(String sql, Tuple params, DBConn conn) {
    return budgetDAO.getBudgetsBySql(sql, params, conn);
  }

  public Future<List<Budget>> getBudgetsByIds(List<String> ids, DBConn conn) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    ids.forEach(id -> criterionBuilder.with("id", id));
    return budgetDAO.getBudgetsByCriterion(criterionBuilder.build(), conn);
  }

  public void clearReadOnlyFields(Budget budgetFromNew) {
    budgetFromNew.setAllocated(null);
    budgetFromNew.setAvailable(null);
    budgetFromNew.setUnavailable(null);
    budgetFromNew.setOverEncumbrance(null);
    budgetFromNew.setOverExpended(null);
    budgetFromNew.setCashBalance(null);
    budgetFromNew.setTotalFunding(null);
  }

  public void updateBudgetsWithCalculatedFields(List<Budget> budgets){
    budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
  }

  public Future<Void> closeBudgets(LedgerFiscalYearRollover rollover, DBConn conn) {
    String sql = String.format(
        "UPDATE %s AS budgets SET jsonb = budgets.jsonb || jsonb_build_object('budgetStatus', 'Closed') "
            + "FROM %s AS fund  WHERE fund.ledgerId = '%s' AND budgets.fiscalYearId = '%s' AND fund.id = budgets.fundId;",
        getFullTableName(conn.getTenantId(), BUDGET_TABLE), getFullTableName(conn.getTenantId(), FUND_TABLE),
        rollover.getLedgerId(), rollover.getFromFiscalYearId());

    return budgetDAO.updateBatchBudgetsBySql(sql, conn)
      .mapEmpty();
  }

  public Future<List<Budget>> getBudgetsByFiscalYearIdsAndFundIdsForUpdate(
      Map<String, Set<String>> fiscalYearIdToFundIds, DBConn conn) {
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT jsonb FROM ")
      .append(getFullTableName(conn.getTenantId(), BUDGET_TABLE))
      .append(" WHERE ");
    Iterator<String> fiscalYearIterator = fiscalYearIdToFundIds.keySet().iterator();
    while (fiscalYearIterator.hasNext()) {
      String fiscalYearId = fiscalYearIterator.next();
      Set<String> fundIds = fiscalYearIdToFundIds.get(fiscalYearId);
      sb.append("(fiscalYearId = '")
        .append(fiscalYearId)
        .append("' AND (");
      Iterator<String> fundIterator = fundIds.iterator();
      while (fundIterator.hasNext()) {
        String fundId = fundIterator.next();
        sb.append("fundId = '")
          .append(fundId)
          .append("'");
        if (fundIterator.hasNext()) {
          sb.append(" OR ");
        }
      }
      sb.append("))");
      if (fiscalYearIterator.hasNext()) {
        sb.append(" OR ");
      }
    }
    sb.append(" FOR UPDATE");
    String sql = sb.toString();
    return budgetDAO.getBudgetsBySql(sql, Tuple.tuple(), conn)
      .map(budgets -> {
        int expectedNumberOfBudgets = fiscalYearIdToFundIds.values().stream().map(Set::size).mapToInt(Integer::intValue).sum();
        if (budgets.size() != expectedNumberOfBudgets) {
          List<String> idsOfFundsWithNoBudget = fiscalYearIdToFundIds.values().stream()
            .flatMap(Collection::stream)
            .distinct()
            .filter(fundId -> budgets.stream().noneMatch(b -> fundId.equals(b.getFundId())))
            .toList();
          throw new HttpException(500, "Could not find some budgets in the database, fund ids=" + idsOfFundsWithNoBudget);
        }
        return budgets;
      });
  }

  private Future<Void> deleteAllocationTransactions(Budget budget, DBConn conn) {
    logger.debug("deleteAllocationTransactions:: Trying to delete allocation transactions with fund id {}", budget.getFundId());

    String sql ="DELETE FROM "+ getFullTableName(conn.getTenantId(), TRANSACTIONS_TABLE)
      + " WHERE  (fromFundId = '" + budget.getFundId() + "' OR toFundId = '" + budget.getFundId() + "')"
      + " AND fiscalYearId = '" + budget.getFiscalYearId() + "' AND jsonb->>'transactionType' = 'Allocation'";

    return conn.execute(sql)
      .onSuccess(ecList -> logger.info("deleteAllocationTransactions:: Allocation transaction for budget with id {} successfully deleted",
        budget.getId()))
      .onFailure(e -> logger.error("deleteAllocationTransactions:: Allocation transaction deletion by query {} failed for budget with id {}",
        sql, budget.getId(), e))
      .mapEmpty();
  }

  private Future<Void> checkTransactions(Budget budget, DBConn conn) {
    logger.debug("checkTransactions:: Checking transactions with fund id {}", budget.getFundId());

    String sql ="SELECT jsonb FROM " + getFullTableName(conn.getTenantId(), TRANSACTIONS_TABLE)
      + " WHERE fiscalYearId = '" + budget.getFiscalYearId() + "'"
      + " AND (fromFundId = '" + budget.getFundId() + "' OR toFundId = '" + budget.getFundId() + "')"
      + " AND (jsonb->>'transactionType' <> 'Allocation'"
      + " OR (jsonb->>'transactionType' = 'Allocation' AND toFundId IS NOT NULL AND fromFundId IS NOT NULL))";

    return conn.execute(sql)
      .map(rowSet -> {
        if (rowSet.size() > 0) {
          logger.error("checkTransactions:: Transaction is present");
          throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR.toError());
        }
        return null;
      })
      .onSuccess(rowSet -> logger.info("checkTransactions:: Transactions for FundId {} have been successfully checked",
        budget.getFundId()))
      .onFailure(e -> logger.error("checkTransactions:: Transaction retrieval by query {} failed for FundId {}",
        sql, budget.getFundId(), e))
      .mapEmpty();
  }

  private Future<Void> unlinkGroupFundFiscalYears(String id, DBConn conn) {
    logger.debug("unlinkGroupFundFiscalYears:: Trying to unlink group fund fiscal years with budget id {}", id);

    String sql = "UPDATE " + getFullTableName(conn.getTenantId(), GROUP_FUND_FY_TABLE)
      + " SET jsonb = jsonb - 'budgetId' WHERE budgetId=$1;";

    return conn.execute(sql, Tuple.of(UUID.fromString(id)))
      .onSuccess(ecList -> logger.info("unlinkGroupFundFiscalYears:: Group fund fiscal years have been successfully unlinked by budget id {}", id))
      .onFailure(e -> logger.error("unlinkGroupFundFiscalYears:: Failed to update group_fund_fiscal_year by budget id {}", id, e))
      .mapEmpty();
  }

}
