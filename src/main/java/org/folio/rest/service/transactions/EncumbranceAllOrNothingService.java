package org.folio.rest.service.transactions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.folio.rest.dao.transactions.EncumbrancesDAO;
import org.folio.rest.dao.transactions.TemporaryOrderTransactionPostgresDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.service.summary.EncumbranceTransactionSummaryService;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;


public class EncumbranceAllOrNothingService extends AllOrNothingTransactionService<OrderTransactionSummary> {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String GROUP_FUND_ID_BY_LEDGERFY = "SELECT ledger_fy.jsonb, funds.id FROM %s AS ledger_fy INNER JOIN %s AS funds"
    + " ON (funds.ledgerId = ledger_fy.ledgerId) INNER JOIN %s AS transactions"
    + " ON (funds.id = transactions.fromFundId)"
    + " WHERE (transactions.encumbrance_sourcePurchaseOrderId = ? AND ledger_fy.fiscalYearId = transactions.fiscalYearId);";

  public static final String SELECT_BUDGETS_BY_ORDER_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId "
    + "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?";

  public static final String INSERT_PERMANENT_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ? "
    + "ON CONFLICT DO NOTHING;";

  public static final String INSERT_TEMPORARY_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) VALUES (?, ?::JSON) "
    + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePurchaseOrderId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePoLineId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'initialAmountEncumbered'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'status'::text))) DO UPDATE SET id = excluded.id RETURNING id;";

  public EncumbranceAllOrNothingService() {
    super(new TemporaryOrderTransactionPostgresDAO(), new EncumbranceTransactionSummaryService(), new EncumbrancesDAO());
  }


  @Override
  Void handleValidationError(Transaction transaction) {
    List<Error> errors = new ArrayList<>();

    errors.addAll(buildNullValidationError(getSummaryId(transaction), "encumbrance"));
    errors.addAll(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }



  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    MultiKeyMap<String, Budget> groupedBudgets = new MultiKeyMap<>();
    groupedBudgets.putAll(budgets.stream().collect(toMap(budget -> new MultiKey<>(budget.getFundId(), budget.getFiscalYearId()), identity())));

    return existingTransactions.stream()
      .collect(groupingBy(
        transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

  }

  @Override
  protected String getSelectBudgetsQuery(String tenantId) {
    return getSelectBudgetsQuery(SELECT_BUDGETS_BY_ORDER_ID, tenantId, TEMPORARY_ORDER_TRANSACTIONS);
  }

  /**
   * To prevent partial encumbrance transactions for an order, all the encumbrances must be created following All or nothing
   */
  @Override
  Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    return transactionsDAO.saveTransactionsToPermanentTable(summaryId, client)
      .compose(updated -> {
        if (updated > 0) {
          return updateBudgetsLedgersTotals(transactions, client);
        }
        return Future.succeededFuture();
      });
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return Optional.ofNullable(transaction.getEncumbrance())
      .map(Encumbrance::getSourcePurchaseOrderId)
      .orElse(null);
  }

  private Future<Void> updateBudgetsLedgersTotals(List<Transaction> transactions, DBClient client) {
    String sql = getSelectBudgetsQuery(client.getTenantId());
    JsonArray params = new JsonArray();
    params.add(getSummaryId(transactions.get(0)));
    return budgetDAO.getBudgets(sql, params, client)
      .compose(oldBudgets -> {
        List<Budget> newBudgets = updateBudgetsTotals(transactions, oldBudgets);
        List<JsonObject> jsonBudgets = newBudgets.stream().map(JsonObject::mapFrom).collect(toList());
        return budgetDAO.updateBatchBudgets(buildUpdateBudgetsQuery(jsonBudgets, client.getTenantId()), client)
          .compose(listTx -> updateLedgerFYsWithTotals(transactions, oldBudgets, newBudgets, client));
      });
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(this::updateBudgetTotals)
      .collect(toList());
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {

          double newEncumbered = sumMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency);
          budget.setEncumbered(newEncumbered);

          recalculateOverEncumbered(budget, currency);
          recalculateAvailableUnavailable(budget, currency);
        });
    }
    return budget;
  }

  private void recalculateOverEncumbered(Budget budget, CurrencyUnit currency) {
    double a = subtractMoneyNonNegative(budget.getAllocated(), budget.getExpenditures(), currency);
    a = subtractMoneyNonNegative(a, budget.getAwaitingPayment(), currency);
    double newOverEncumbrance = subtractMoneyNonNegative(budget.getEncumbered(), a, currency);
    budget.setOverEncumbrance(newOverEncumbrance);
  }

  private void recalculateAvailableUnavailable(Budget budget, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoneyNonNegative(budget.getAllocated(), newUnavailable, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
  }

  protected Future<Void> updateLedgerFYsWithTotals(List<Transaction> transactions, List<Budget> oldBudgets, List<Budget> newBudgets, DBClient client) {
    String currency = transactions.get(0).getCurrency();
    String summaryId = getSummaryId(transactions.get(0));
    Map<String, MonetaryAmount> oldAvailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> oldUnavailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> newAvailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> newUnavailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> availableDifference = getAmountDifference(oldAvailableByFundId, newAvailableByFundId);

    Map<String, MonetaryAmount> unavailableDifference = getAmountDifference(oldUnavailableByFundId, newUnavailableByFundId);

    return groupFundIdsByLedgerFy(summaryId, client)
      .map(ledgerFYListMap -> calculateLedgerFyTotals(ledgerFYListMap, availableDifference, unavailableDifference))
      .compose(ledgers -> updateLedgerFYs(ledgers, client));
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

  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, Set<String>> groupedLedgerFYs, Map<String, MonetaryAmount> availableDifference, Map<String, MonetaryAmount> unavailableDifference) {
    return groupedLedgerFYs.entrySet().stream().map(ledgerFYListEntry -> updateLedgerFY(ledgerFYListEntry, availableDifference, unavailableDifference)).collect(toList());
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

  private Future<Map<LedgerFY, Set<String>>> groupFundIdsByLedgerFy(String summaryId, DBClient client) {
    Promise<Map<LedgerFY, Set<String>>> promise = Promise.promise();
    String sql = getLedgerFYsQuery(client.getTenantId());
    JsonArray params = new JsonArray();
    params.add(summaryId);
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          Map<LedgerFY, Set<String>> ledgers = reply.result()
            .getResults()
            .stream()
            .collect(ledgerFYFundIdsMapping());
          promise.complete(ledgers);
        }
      });
    return promise.future();
  }

  private Collector<JsonArray, ?, HashMap<LedgerFY, Set<String>>> ledgerFYFundIdsMapping() {
    int ledgerFYColumnNumber = 0;
    int fundIdColumnNumber = 1;
    return toMap(o -> new JsonObject(o.getString(ledgerFYColumnNumber)).mapTo(LedgerFY.class),
      o -> Collections.singleton(o.getString(fundIdColumnNumber)), (o, o2) -> {
        Set<String> newList = new HashSet<>(o);
        newList.addAll(o2);
        return newList;
      }, HashMap::new);
  }

  public String getLedgerFYsQuery(String tenantId) {
    return String.format(GROUP_FUND_ID_BY_LEDGERFY, getFullTableName(tenantId, LEDGERFY_TABLE),
        getFullTableName(tenantId, FUND_TABLE), getFullTableName(tenantId, TEMPORARY_ORDER_TRANSACTIONS));
  }

  @Override
  public Future<Void> updateTransaction(String id, Transaction transaction, Context context, Map<String, String> okapiHeaders) {
    DBClient client = new DBClient(context, okapiHeaders);
    return isTransactionExists(id, client).compose(transactionExists -> {
      if (transactionExists) {
        return updateEncumbrance(transaction, context, okapiHeaders);
      } else {
        return createTransaction(transaction, context, okapiHeaders).mapEmpty();
      }
    });
  }

  public Future<Void> updateEncumbrance(Transaction transaction, Context context, Map<String, String> okapiHeaders) {
    try {
      handleValidationError(transaction);
    } catch (HttpStatusException e) {
      return Future.failedFuture(e);
    }
    DBClient client = new DBClient(context, okapiHeaders);

    return transactionSummaryService.getTransactionSummary(transaction, context, okapiHeaders)
      .compose(summary -> collectTempTransactions(transaction, context, okapiHeaders).compose(transactions -> {
        if (transactions.size() == transactionSummaryService.getNumTransactions(summary)) {
          return client.startTx()
            .compose(c -> processTransactionsUponUpdate(transactions, client))
            .compose(vVoid -> moveFromTempToPermanentTable(summary, client))
            .compose(vVoid -> client.endTx())
            .onComplete(result -> {
              if (result.failed()) {
                log.error("Transactions or associated data failed to be processed", result.cause());
                client.rollbackTransaction();
              } else {
                log.info("Transactions and associated data were successfully processed");
              }
            });

        } else {
          return Future.succeededFuture();
        }
      }));
  }


    private Future<Boolean> isTransactionExists(String transactionId, DBClient client) {
    Promise<Boolean> promise = Promise.promise();
    client.getPgClient().getById(TRANSACTION_TABLE, transactionId, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(reply.result() != null);
      }
    });
    return promise.future();
  }

  private Future<Void> processTransactionsUponUpdate(List<Transaction> newTransactions, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    newTransactions.stream()
      .map(Transaction::getId)
      .forEach(id -> criterionBuilder.with("id", id));
    return transactionsDAO.getTransactions(criterionBuilder.build(), client)
      .compose(existingTransactions -> updateBudgetsTotals(existingTransactions, newTransactions, client)
        .map(listTx -> excludeReleasedEncumbrances(newTransactions, existingTransactions)))
      .compose(transactions -> transactionsDAO.updatePermanentTransactions(transactions, client));
  }

  private List<Transaction> excludeReleasedEncumbrances(List<Transaction> newTransactions, List<Transaction> existingTransactions) {
    Map<String, Transaction> groupedTransactions = existingTransactions.stream().collect(toMap(Transaction::getId, identity()));
    return newTransactions.stream()
      .filter(transaction -> groupedTransactions.get(transaction.getId()).getEncumbrance().getStatus() != Encumbrance.Status.RELEASED)
      .collect(Collectors.toList());
  }

  private Future<Integer> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> newTransactions, DBClient client) {
    JsonArray params = new JsonArray();
    params.add(getSummaryId(newTransactions.get(0)));
    return budgetDAO.getBudgets(getSelectBudgetsQuery(client.getTenantId()), params, client)
      .map(budgets -> updateBudgetsTotals(existingTransactions, newTransactions, budgets))
      .compose(budgets -> {
        List<JsonObject> jsonBudgets = budgets.stream().map(JsonObject::mapFrom).collect(toList());
        String sql = buildUpdateBudgetsQuery(jsonBudgets, client.getTenantId());
        return budgetDAO.updateBatchBudgets(sql, client);
      });
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<String, Transaction> existingGrouped = existingTransactions.stream().collect(toMap(Transaction::getId, identity()));
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(listEntry -> updateBudgetTotals(listEntry, existingGrouped))
      .collect(Collectors.toList());
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry, Map<String, Transaction> existingGrouped) {
    Budget budget = entry.getKey();

    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          Transaction existingTransaction = existingGrouped.get(tmpTransaction.getId());
          if (!isEncumbranceReleased(existingTransaction)) {
            processBudget(budget, currency, tmpTransaction, existingTransaction);
            if (isEncumbranceReleased(tmpTransaction)) {
              releaseEncumbrance(budget, currency, tmpTransaction);
            }
          }
        });
    }
    return budget;
  }

  private void releaseEncumbrance(Budget budget, CurrencyUnit currency, Transaction tmpTransaction) {
    //encumbered decreases by the amount being released
    budget.setEncumbered(subtractMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency));

    // available increases by the amount being released
    budget.setAvailable(sumMoney(budget.getAvailable(), tmpTransaction.getAmount(), currency));

    // unavailable decreases by the amount being released (min 0)
    double newUnavailable = subtractMoney(budget.getUnavailable(), tmpTransaction.getAmount(), currency);
    budget.setUnavailable(newUnavailable < 0 ? 0 : newUnavailable);

    // transaction.amount becomes 0 (save the original value for updating the budget)
    tmpTransaction.setAmount(0d);
  }

  private void processBudget(Budget budget, CurrencyUnit currency, Transaction tmpTransaction, Transaction existingTransaction) {
    // encumbered decreases by the difference between provided and previous transaction.encumbrance.amountAwaitingPayment values
    double newEncumbered = subtractMoney(budget.getEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newEncumbered = sumMoney(newEncumbered, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    budget.setEncumbered(newEncumbered);

    // awaitingPayment increases by the same amount
    double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newAwaitingPayment = subtractMoneyNonNegative(newAwaitingPayment, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    budget.setAwaitingPayment(newAwaitingPayment);

    // encumbrance transaction.amount is updated to (initial encumbrance - awaiting payment - expended)
    double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newAmount = subtractMoney(newAmount, tmpTransaction.getEncumbrance().getAmountExpended(), currency);
    tmpTransaction.setAmount(newAmount);
  }


  private boolean isEncumbranceReleased(Transaction transaction) {
    return transaction.getEncumbrance()
      .getStatus() == Encumbrance.Status.RELEASED;
  }

}
