package org.folio.rest.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.TransactionSummaryAPI.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.handleFailure;
import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.ws.rs.core.Response;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;



public class OrderTransactionsHandler extends AllOrNothingHandler {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public OrderTransactionsHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> asyncResultHandler) {
    super(TEMPORARY_ORDER_TRANSACTIONS, ORDER_TRANSACTION_SUMMARIES, okapiHeaders, ctx, asyncResultHandler);
  }


  @Override
  String getSummaryId(Transaction transaction) {
    return Optional.ofNullable(transaction.getEncumbrance())
      .map(Encumbrance::getSourcePurchaseOrderId)
      .orElse(null);
  }

  @Override
  Criterion getTransactionBySummaryIdCriterion(String value) {
    return getCriterionByFieldNameAndValueNotJsonb("encumbrance_sourcePurchaseOrderId", "=", value);
  }

  @Override
  void handleValidationError(Transaction transaction) {
    List<Error> errors = new ArrayList<>();

    errors.addAll(buildNullValidationError(getSummaryId(transaction), "encumbrance"));
    errors.addAll(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
  }


  @Override
  String createTempTransactionQuery() {
    return "INSERT INTO " + getFullTemporaryTransactionTableName() + " (id, jsonb) VALUES (?, ?::JSON) "
        + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), " + "lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePurchaseOrderId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePoLineId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'initialAmountEncumbered'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'status'::text))) " + "DO UPDATE SET id = excluded.id RETURNING id;";
  }

  @Override
  String createPermanentTransactionsQuery() {
    return String.format("INSERT INTO %s (id, jsonb) " + "SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ? "
        + "ON CONFLICT DO NOTHING;", getFullTransactionTableName(), getTemporaryTransactionTable());
  }

  @Override
  protected String getBudgetsQuery() {
    return "SELECT DISTINCT ON (budgets.id) budgets.jsonb " +
    "FROM " + getFullTableName(getTenantId(), BUDGET_TABLE) + " AS budgets " +
    "INNER JOIN "+ getFullTemporaryTransactionTableName() + " AS transactions " +
    "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId " +
    "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?";
  }

  /**
   * To prevent partial encumbrance transactions for an order, all the encumbrances must be created following All or nothing
   */
  @Override
  Future<Tx<List<Transaction>>> processTemporaryToPermanentTransactions(Tx<List<Transaction>> tx) {
    return createPermanentTransactions(tx);
  }

}
