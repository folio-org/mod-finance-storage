package org.folio.service.transactions.batch;

import io.vertx.core.Future;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionPatch;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.GroupedCriterias;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.CREDIT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;

public class BatchTransactionHolder {
  private static final String TRANSACTION_TYPE = "transactionType";
  private static final String SOURCE_INVOICE_ID = "sourceInvoiceId";

  private final BatchTransactionDAO transactionDAO;
  private final FundService fundService;
  private final BudgetService budgetService;
  private final LedgerService ledgerService;

  private List<Transaction> allTransactionsToCreate;
  private List<Transaction> allTransactionsToUpdate;
  private List<TransactionPatch> allTransactionPatches;
  private Set<String> idsOfTransactionsToDelete;
  private List<Transaction> allTransactionsToCreateOrUpdate;
  private List<Transaction> existingTransactions;
  private List<Transaction> linkedEncumbrances;
  private List<Transaction> linkedPendingPayments;
  private List<Transaction> allTransactions;
  private List<Fund> allFunds;
  private List<Budget> allBudgets;
  private List<Ledger> allLedgers;
  private Map<String, Boolean> budgetIdToRestrictedExpenditures;
  private Map<String, Boolean> budgetIdToRestrictedEncumbrance;
  private Map<String, Transaction> existingTransactionMap;

  public BatchTransactionHolder(BatchTransactionDAO transactionDAO, FundService fundService, BudgetService budgetService,
      LedgerService ledgerService) {
    this.transactionDAO = transactionDAO;
    this.fundService = fundService;
    this.budgetService = budgetService;
    this.ledgerService = ledgerService;
  }

  public Future<Void> setup(Batch batch, DBConn conn) {
    allTransactionsToCreate = batch.getTransactionsToCreate();
    allTransactionsToUpdate = new ArrayList<>(batch.getTransactionsToUpdate());
    allTransactionPatches = new ArrayList<>(batch.getTransactionPatches());
    idsOfTransactionsToDelete = new HashSet<>(batch.getIdsOfTransactionsToDelete());
    allTransactionsToCreateOrUpdate = Stream.concat(allTransactionsToCreate.stream(), allTransactionsToUpdate.stream())
      .collect(toCollection(ArrayList::new));
    return BatchTransactionChecks.checkTransactionsToDelete(idsOfTransactionsToDelete, transactionDAO, conn)
      .compose(v -> loadExistingTransactions(conn))
      .compose(v -> loadLinkedEncumbrances(conn))
      .compose(v -> loadLinkedPendingPayments(conn))
      .map(v -> {
        allTransactions = Stream.of(allTransactionsToCreateOrUpdate.stream(), existingTransactions.stream(),
          linkedEncumbrances.stream())
          .flatMap(Function.identity())
          .toList();
        return null;
      })
      .compose(v -> loadFunds(conn))
      .compose(v -> loadBudgets(conn))
      .compose(v -> loadLedgers(conn))
      .map(v -> {
        buildOverspendMaps();
        return null;
      });
  }

  public List<Transaction> getAllTransactionsToCreate() {
    return allTransactionsToCreate;
  }

  public List<Transaction> getAllTransactionsToUpdate() {
    return allTransactionsToUpdate;
  }

  public List<TransactionPatch> getAllTransactionPatches() {
    return allTransactionPatches;
  }

  public List<String> getIdsOfTransactionsToDelete() {
    return idsOfTransactionsToDelete.stream().toList();
  }

  public Map<String, Transaction> getExistingTransactionMap() {
    return existingTransactionMap;
  }

  public List<Budget> getBudgets() {
    return allBudgets;
  }

  public Map<String, Transaction> getLinkedEncumbranceMap() {
    return linkedEncumbrances.stream().collect(toMap(Transaction::getId, Function.identity()));
  }

  public List<Transaction> getLinkedPendingPayments() {
    return linkedPendingPayments;
  }

  public boolean budgetExpendituresAreRestricted(String budgetId) {
    return budgetIdToRestrictedExpenditures.get(budgetId);
  }

  public boolean budgetEncumbranceIsRestricted(String budgetId) {
    return budgetIdToRestrictedEncumbrance.get(budgetId);
  }

  public void addTransactionToUpdate(Transaction transactionToUpdate, Transaction existingTransaction) {
    transactionToUpdate.getMetadata().setUpdatedDate(new Date());
    allTransactionsToUpdate.add(transactionToUpdate);
    allTransactionsToCreateOrUpdate.add(transactionToUpdate);
    existingTransactions.add(existingTransaction);
    existingTransactionMap.put(existingTransaction.getId(), existingTransaction);
  }

  public void addTransactionToDelete(String id) {
    idsOfTransactionsToDelete.add(id);
  }

  public String getFundCodeForBudget(Budget budget) {
    // NOTE: this is only used for error messages
    String fundId = budget.getFundId();
    return allFunds.stream()
      .filter(f -> f.getId().equals(fundId))
      .findFirst()
      .map(Fund::getCode)
      .orElse(String.format("Could not find fund code for budget %s", budget.getId()));
  }

  public String getCurrency() {
    return allTransactions.get(0).getCurrency();
  }

  private Future<Void> loadExistingTransactions(DBConn conn) {
    List<String> ids = allTransactionsToCreateOrUpdate.stream().map(Transaction::getId).toList();
    return transactionDAO.getTransactionsByIds(ids, conn)
      .map(transactions -> {
        existingTransactions = new ArrayList<>(transactions);
        existingTransactionMap = existingTransactions.stream().collect(Collectors.toMap(Transaction::getId, Function.identity()));
        BatchTransactionChecks.checkExistingTransactionsConsistency(allTransactionsToCreate, allTransactionsToUpdate, existingTransactionMap);
        return null;
      });
  }

  private Future<Void> loadLinkedEncumbrances(DBConn conn) {
    List<String> ids = allTransactionsToCreateOrUpdate.stream().map(tr -> {
      if (List.of(PAYMENT, CREDIT).contains(tr.getTransactionType())) {
        return tr.getPaymentEncumbranceId();
      } else if (tr.getTransactionType() == PENDING_PAYMENT && tr.getAwaitingPayment() != null) {
        return tr.getAwaitingPayment().getEncumbranceId();
      } else {
        return null;
      }
    }).filter(Objects::nonNull)
      .distinct()
      .toList();
    return transactionDAO.getTransactionsByIds(ids, conn)
      .map(transactions -> {
        linkedEncumbrances = transactions;
        return null;
      });
  }

  private Future<Void> loadLinkedPendingPayments(DBConn conn) {
    List<String> invoiceIds = allTransactionsToCreate.stream()
      .filter(tr -> List.of(PAYMENT, CREDIT).contains(tr.getTransactionType()))
      .map(Transaction::getSourceInvoiceId)
      .distinct()
      .toList();
    if (invoiceIds.isEmpty()) {
      return succeededFuture();
    }
    Criteria trTypeCrit = new Criteria()
      .addField("'" + TRANSACTION_TYPE + "'")
      .setOperation("=")
      .setVal(PENDING_PAYMENT.value());
    GroupedCriterias invoiceIdGroup = new GroupedCriterias();
    invoiceIds.forEach(id -> invoiceIdGroup.addCriteria(
      new Criteria()
        .addField("'" + SOURCE_INVOICE_ID + "'")
        .setOperation("=")
        .setVal(id),
      "OR"));
    Criterion criterion = new Criterion();
    criterion.addCriterion(trTypeCrit);
    criterion.addGroupOfCriterias(invoiceIdGroup);
    return transactionDAO.getTransactionsByCriterion(criterion, conn)
      .map(transactions -> {
        linkedPendingPayments = transactions;
        return null;
      });
  }

  private Future<Void> loadFunds(DBConn conn) {
    List<String> fundIds = allTransactions.stream()
      .map(tr -> {
        ArrayList<String> list = new ArrayList<>();
        list.add(tr.getFromFundId());
        list.add(tr.getToFundId());
        return list;
      })
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (fundIds.isEmpty()) {
      allFunds = emptyList();
      return succeededFuture();
    }
    return fundService.getFundsByIds(fundIds, conn)
      .map(funds -> {
        allFunds = funds;
        return null;
      });
  }

  private Future<Void> loadBudgets(DBConn conn) {
    Map<String, Set<String>> fiscalYearIdToFundIds = allTransactions.stream().collect(groupingBy(Transaction::getFiscalYearId,
      flatMapping(tr -> (Stream<String>)Stream.of(tr.getFromFundId(), tr.getToFundId()).filter(Objects::nonNull), toSet())));
    if (fiscalYearIdToFundIds.isEmpty()) {
      allBudgets = emptyList();
      return succeededFuture();
    }
    return budgetService.getBudgetsByFiscalYearIdsAndFundIdsForUpdate(fiscalYearIdToFundIds, conn)
      .map(budgets -> {
        allBudgets = budgets;
        return null;
      });
  }

  private Future<Void> loadLedgers(DBConn conn) {
    List<String> ledgerIds = allFunds.stream().map(Fund::getLedgerId).distinct().toList();
    if (ledgerIds.isEmpty()) {
      allLedgers = emptyList();
      return succeededFuture();
    }
    return ledgerService.getLedgersByIds(ledgerIds, conn)
      .map(ledgers -> {
        allLedgers = ledgers;
        return null;
      });
  }

  private void buildOverspendMaps() {
    Map<String, Fund> fundMap = allFunds.stream().collect(Collectors.toMap(Fund::getId, Function.identity()));
    Map<String, Ledger> ledgerMap = allLedgers.stream().collect(Collectors.toMap(Ledger::getId, Function.identity()));
    budgetIdToRestrictedExpenditures = new HashMap<>();
    budgetIdToRestrictedEncumbrance = new HashMap<>();
    for (Budget b : allBudgets) {
      Fund f = fundMap.get(b.getFundId());
      Ledger l = ledgerMap.get(f.getLedgerId());
      budgetIdToRestrictedExpenditures.put(b.getId(), l.getRestrictExpenditures() && b.getAllowableExpenditure() != null);
      budgetIdToRestrictedEncumbrance.put(b.getId(), l.getRestrictEncumbrance() && b.getAllowableEncumbrance() != null);
    }
  }
}
