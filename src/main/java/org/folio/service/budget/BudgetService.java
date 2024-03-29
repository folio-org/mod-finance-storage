package org.folio.service.budget;

import static java.util.Collections.singletonList;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ErrorCodes.*;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.*;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.budget.BudgetDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.util.ErrorCodes;
import org.folio.utils.CalculationUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Tuple;

public class BudgetService {

  private static final Logger logger = LogManager.getLogger(BudgetService.class);

  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";
  private static final String TRANSACTIONS_TABLE = "transaction";
  public static final String TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR = "transactionIsPresentBudgetDeleteError";

  public static final String SELECT_BUDGETS_BY_FY_AND_FUND_FOR_UPDATE = "SELECT jsonb FROM %s "
    + "WHERE fiscalYearId = $1 AND fundId = $2 FOR UPDATE";

  private final BudgetDAO budgetDAO;

  public BudgetService(BudgetDAO budgetDAO) {
    this.budgetDAO = budgetDAO;
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

  public Future<Budget> getBudgetByFundIdAndFiscalYearId(String fiscalYearId, String fundId, DBConn conn) {
    logger.debug("getBudgetByFundIdAndFiscalYearId:: Trying to get budget by fund id {} and fiscal year id {}", fundId, fiscalYearId);

    Criterion criterion = new CriterionBuilder().with("fundId", fundId)
      .with("fiscalYearId", fiscalYearId)
      .build();
    return budgetDAO.getBudgets(criterion, conn)
      .map(budgets -> {
        if (budgets.isEmpty()) {
          logger.error("getBudgetByFundIdAndFiscalYearId:: Budget not found for fundId {} and fiscalYearId {}", fundId, fiscalYearId);
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(),
            JsonObject.mapFrom(BUDGET_NOT_FOUND_FOR_TRANSACTION.toError()).encodePrettily());
        } else {
          logger.info("getBudgetByFundIdAndFiscalYearId:: Successfully retrieved budget by fund id {} and fiscal year id {}", fundId, fiscalYearId);
          return budgets.get(0);
        }
      })
      .onFailure(e -> logger.error("getBudgetByFundIdAndFiscalYearId:: Getting budget by fund id {} and fiscal year id {} failed", fundId, fiscalYearId, e));
  }

  /**
   * Get budget by fiscal year id and fund id for update.
   * This should only be called in a transactional context because it is using SELECT FOR UPDATE.
   */
  public Future<Budget> getBudgetByFiscalYearIdAndFundIdForUpdate(String fiscalYearId, String fundId, DBConn conn) {
    logger.debug("getBudgetByFiscalYearIdAndFundIdForUpdate:: Trying to get budget by fund id {} and fiscal year id {} for update", fundId, fiscalYearId);

    String sql = getSelectBudgetQueryByFyAndFundForUpdate(conn.getTenantId());
    return budgetDAO.getBudgetsBySql(sql, Tuple.of(fiscalYearId, fundId), conn)
      .map(budgets -> {
        if (budgets.isEmpty()) {
          logger.error("getBudgetByFiscalYearIdAndFundIdForUpdate:: Budget for update not found for fundId {} and fiscalYearId {}", fundId, fiscalYearId);
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(),
            JsonObject.mapFrom(BUDGET_NOT_FOUND_FOR_TRANSACTION.toError()).encodePrettily());
        } else {
          return budgets.get(0);
        }
      })
      .onFailure(e -> logger.error("getBudgetByFiscalYearIdAndFundIdForUpdate:: Getting budget by fund id {} and fiscal year id {} failed",
        fundId, fiscalYearId, e));
  }

  public Future<Void> updateBatchBudgets(List<Budget> budgets, DBConn conn) {
    budgets.forEach(this::clearReadOnlyFields);
    return budgetDAO.updateBatchBudgets(budgets, conn);
  }

  public Future<List<Budget>> getBudgets(String sql, Tuple params, DBConn conn) {
    return budgetDAO.getBudgetsBySql(sql, params, conn);
  }

  public void updateBudgetMetadata(Budget budget, Transaction transaction) {
    budget.getMetadata()
      .setUpdatedDate(transaction.getMetadata()
        .getUpdatedDate());
    budget.getMetadata()
      .setUpdatedByUserId(transaction.getMetadata()
        .getUpdatedByUserId());
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

  /**
   * Checks if there is enough budget money available for a transaction.
   * This method is used exclusively for "Move allocation" action and is skipped for other types.
   *
   * @param transaction The transaction to be checked.
   * @param conn      database connection
   * @return A {@link Future} that completes successfully if there is enough money available,
   * or fails with a {@link HttpException} if not enough money is available.
   */
  public Future<Void> checkBudgetHaveMoneyForTransaction(Transaction transaction, DBConn conn) {
    if (isNotMoveAllocation(transaction)) {
      return Future.succeededFuture();
    }

    return getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), transaction.getFromFundId(), conn)
      .map(budget -> {
        if (budget.getAvailable() < transaction.getAmount()) {
          ErrorCodes errorCode = transaction.getTransactionType() == Transaction.TransactionType.ALLOCATION ?
            NOT_ENOUGH_MONEY_FOR_ALLOCATION : GENERIC_ERROR_CODE;
          logger.error(errorCode.getDescription());
          throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(),
            JsonObject.mapFrom(new Errors().withErrors(singletonList(errorCode.toError())).withTotalRecords(1)).encode());
        }
        return null;
      });
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
    return budgetDAO.getBudgetsBySql(sql, Tuple.tuple(), conn);
  }

  private Future<Void> deleteAllocationTransactions(Budget budget, DBConn conn) {
    logger.debug("deleteAllocationTransactions:: Trying to delete allocation transactions with fund id {}", budget.getFundId());

    String sql ="DELETE FROM "+ getFullTableName(conn.getTenantId(), TRANSACTIONS_TABLE)
      + " WHERE  (fromFundId = '" + budget.getFundId() + "' OR toFundId = '" + budget.getFundId() + "')"
      + " AND fiscalYearId = '" + budget.getFiscalYearId() + "' AND lower(f_unaccent(jsonb->>'transactionType'::text)) = 'allocation'";

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
      + " AND (lower(f_unaccent(jsonb->>'transactionType'::text)) <> 'allocation'"
      + " OR (lower(f_unaccent(jsonb->>'transactionType'::text)) = 'allocation' AND toFundId IS NOT NULL AND fromFundId IS NOT NULL))";

    return conn.execute(sql)
      .map(rowSet -> {
        if (rowSet.size() > 0) {
          logger.error("checkTransactions:: Transaction is present");
          throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR);
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

  private String getSelectBudgetQueryByFyAndFundForUpdate(String tenantId){
    String budgetTableName = getFullTableName(tenantId, BUDGET_TABLE);
    return String.format(SELECT_BUDGETS_BY_FY_AND_FUND_FOR_UPDATE, budgetTableName);
  }

  private boolean isNotMoveAllocation(Transaction transaction) {
    return ObjectUtils.anyNull(transaction.getFromFundId(), transaction.getToFundId())
      && transaction.getTransactionType() == Transaction.TransactionType.ALLOCATION;
  }

}
