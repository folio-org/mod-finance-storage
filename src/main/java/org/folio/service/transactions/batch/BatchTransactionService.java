package org.folio.service.transactions.batch;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.rest.jaxrs.model.TransactionPatch;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.CREDIT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.TRANSFER;
import static org.folio.utils.MetadataUtils.generateMetadata;

public class BatchTransactionService {

  private static final Logger logger = LogManager.getLogger();

  private static final List<TransactionType> transactionTypesInOrder = List.of(ALLOCATION, TRANSFER, PAYMENT,
    PENDING_PAYMENT, ENCUMBRANCE);
  private final DBClientFactory dbClientFactory;
  private final BatchTransactionDAO transactionDAO;
  private final FundService fundService;
  private final BudgetService budgetService;
  private final LedgerService ledgerService;
  private final Map<TransactionType, BatchTransactionServiceInterface> serviceMap;

  public BatchTransactionService(DBClientFactory dbClientFactory, BatchTransactionDAO transactionDAO,
                                 FundService fundService, BudgetService budgetService, LedgerService ledgerService,
                                 Set<BatchTransactionServiceInterface> batchTransactionStrategies) {
    this.dbClientFactory = dbClientFactory;
    this.transactionDAO = transactionDAO;
    this.fundService = fundService;
    this.budgetService = budgetService;
    this.ledgerService = ledgerService;
    serviceMap = new EnumMap<>(TransactionType.class);
    batchTransactionStrategies.forEach(
      strategy -> serviceMap.put(strategy.getTransactionType(), strategy));
  }

  public Future<Void> processBatch(Batch batch, RequestContext requestContext) {
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return client.withTrans(conn -> processBatch(batch, conn, requestContext.getHeaders()));
  }

  public Future<Void> processBatch(Batch batch, DBConn conn, Map<String, String> okapiHeaders) {
    populateMetadata(batch, okapiHeaders);
    try {
      BatchTransactionChecks.sanityChecks(batch);
    } catch (Exception ex) {
      logger.error("Sanity checks before processing batch transactions failed, batch={}", Json.encode(batch), ex);
      return Future.failedFuture(ex);
    }
    BatchTransactionHolder holder = new BatchTransactionHolder(transactionDAO, fundService, budgetService, ledgerService);
    return holder.setup(batch, conn)
      .map(v -> {
        BatchTransactionChecks.checkBudgetsAreActive(holder);
        prepareCreatingTransactions(holder);
        prepareDeletingTransactions(holder, okapiHeaders);
        prepareUpdatingTransactions(holder);
        preparePatchingTransactions(holder);
        BatchTransactionChecks.checkRestrictedBudgets(holder);
        return null;
      })
      .compose(v -> applyChanges(holder, conn))
      .onSuccess(v -> logger.info("All batch transaction operations were successful."))
      .onFailure(t -> logger.error("Error when batch processing transactions, batch={}",
        Json.encode(batch), t));
  }

  private void populateMetadata(Batch batch, Map<String, String> okapiHeaders) {
    Metadata newMd = generateMetadata(okapiHeaders);
    for (Transaction tr : batch.getTransactionsToCreate()) {
      if (tr.getMetadata() == null) {
        tr.setMetadata(JsonObject.mapFrom(newMd).mapTo(Metadata.class));
      }
    }
    for (Transaction tr : batch.getTransactionsToUpdate()) {
      Metadata md = tr.getMetadata();
      md.setUpdatedDate(newMd.getUpdatedDate());
      md.setUpdatedByUserId(newMd.getUpdatedByUserId());
    }
  }

  private void prepareCreatingTransactions(BatchTransactionHolder holder) {
    if (holder.getAllTransactionsToCreate().isEmpty()) {
      return;
    }
    try {
      int totalPrepared = 0;
      for (TransactionType trType : transactionTypesInOrder) {
        List<Transaction> transactionsForType = getByType(trType, holder.getAllTransactionsToCreate());
        if (!transactionsForType.isEmpty()) {
          getBatchServiceForType(trType).prepareCreatingTransactions(transactionsForType, holder);
          totalPrepared += transactionsForType.size();
        }
      }
      logger.info("Successfully prepared {} transactions for creation", totalPrepared);
    } catch (Exception ex) {
      List<String> ids = holder.getAllTransactionsToCreate().stream().map(Transaction::getId).toList();
      logger.error("Failed to prepare transactions for creation, ids = {}", ids, ex);
      throw ex;
    }
  }

  private void prepareUpdatingTransactions(BatchTransactionHolder holder) {
    if (holder.getAllTransactionsToUpdate().isEmpty()) {
      return;
    }
    try {
      // NOTE: holder.getAllTransactionsToUpdate() can change after processing payments/pending payments,
      // so we don't group transactions by type before processing them.
      int totalPrepared = 0;
      for (TransactionType trType : transactionTypesInOrder) {
        List<Transaction> transactionsForType = getByType(trType, holder.getAllTransactionsToUpdate());
        if (!transactionsForType.isEmpty()) {
          getBatchServiceForType(trType).prepareUpdatingTransactions(transactionsForType, holder);
          totalPrepared += transactionsForType.size();
        }
      }
      logger.info("Successfully prepared {} transactions for update", totalPrepared);
    } catch (Exception ex) {
      List<String> ids = holder.getAllTransactionsToUpdate().stream().map(Transaction::getId).toList();
      logger.error("Failed to prepare transactions for update, ids = {}", ids, ex);
      throw ex;
    }
  }

  private void preparePatchingTransactions(BatchTransactionHolder holder) {
    List<TransactionPatch> transactionPatches = holder.getAllTransactionPatches();
    if (transactionPatches.isEmpty()) {
      return;
    }
    throw new HttpException(500, "transactionPatches: not implemented");
  }

  private void prepareDeletingTransactions(BatchTransactionHolder holder, Map<String, String> okapiHeaders) {
    if (holder.getTransactionsToCancelAndDelete().isEmpty()) {
      return;
    }
    // For now only prepare deleting pending payments, there is no budget or encumbrance update for other types.
    // This needs to be done before or during transaction updates, because encumbrances will have to be updated afterward.
    try {
      List<Transaction> pendingPaymentsToDelete = holder.getTransactionsToCancelAndDelete().stream()
        .filter(tr -> tr.getTransactionType() == PENDING_PAYMENT)
        .toList();

      // Update transaction metadata which will be used to update the budget metadata
      Metadata newMd = generateMetadata(okapiHeaders);
      for (Transaction tr : pendingPaymentsToDelete) {
        Metadata md = tr.getMetadata();
        md.setUpdatedDate(newMd.getUpdatedDate());
        md.setUpdatedByUserId(newMd.getUpdatedByUserId());
      }

      getBatchServiceForType(PENDING_PAYMENT).prepareDeletingTransactions(pendingPaymentsToDelete, holder);
      logger.info("Successfully prepared {} pending payments for deletion", pendingPaymentsToDelete.size());
    } catch (Exception ex) {
      List<String> ids = holder.getTransactionsToCancelAndDelete().stream().map(Transaction::getId).toList();
      logger.error("Failed to prepare pending payments for deletion, ids = {}", ids, ex);
      throw ex;
    }
  }

  private Future<Void> applyChanges(BatchTransactionHolder holder, DBConn conn) {
    return createTransactions(holder, conn)
      .compose(v -> updateTransactions(holder, conn))
      .compose(v -> deleteTransactions(holder, conn))
      .compose(v -> updateBudgets(holder, conn));
  }

  private Future<Void> createTransactions(BatchTransactionHolder holder, DBConn conn) {
    List<Transaction> transactions = holder.getAllTransactionsToCreate();
    if (transactions.isEmpty()) {
      return succeededFuture();
    }
    return transactionDAO.createTransactions(transactions, conn)
      .onSuccess(ids -> logger.info("Batch transactions: successfully created {} transactions", transactions.size()))
      .onFailure(t -> logger.error("Batch transactions: failed to create transactions, transactions = {}",
        Json.encode(transactions), t))
      .mapEmpty();
  }

  private Future<Void> updateTransactions(BatchTransactionHolder holder, DBConn conn) {
    List<Transaction> transactions = holder.getAllTransactionsToUpdate();
    if (transactions.isEmpty()) {
      return succeededFuture();
    }
    relinkPaymentsIfNeeded(holder, transactions);
    return transactionDAO.updateTransactions(transactions, conn)
      .onSuccess(v -> logger.info("Batch transactions: successfully updated {} transactions", transactions.size()))
      .onFailure(t -> logger.error("Batch transactions: failed to update transactions, transactions = {}",
        Json.encode(transactions), t));
  }

  private void relinkPaymentsIfNeeded(BatchTransactionHolder holder, List<Transaction> transactions) {
    List<Transaction> payments = holder.getLinkedPayments();
    List<Transaction> createTransactions = holder.getAllTransactionsToCreate();
    List<Transaction> deletingTransactions = holder.getAllTransactionsToDelete();
    if (CollectionUtils.isEmpty(payments) || CollectionUtils.isEmpty(createTransactions) || CollectionUtils.isEmpty(deletingTransactions)) {
      return;
    }
    payments.forEach(payment -> {
      var oldEncumbrance = deletingTransactions.stream()
        .filter(Objects::nonNull)
        .filter(tr -> tr.getTransactionType() == ENCUMBRANCE)
        .filter(tr -> StringUtils.equals(payment.getPaymentEncumbranceId(), tr.getId()))
        .findFirst().orElse(null);
      Transaction newEncumbrance = null;
      if (Objects.nonNull(oldEncumbrance)) {
        logger.info("relinkPaymentsIfNeeded:: Old encumbrance id={}, from fund id={}", oldEncumbrance.getId(), oldEncumbrance.getFromFundId());
        newEncumbrance = createTransactions.stream()
          .filter(Objects::nonNull)
          .filter(tr -> isValidAnalogousEncumbrance(tr, oldEncumbrance))
          .filter(tr -> Objects.nonNull(tr.getId()))
          .findFirst().orElse(null);
      }
      if (Objects.nonNull(newEncumbrance)) {
        logger.info("relinkPaymentsIfNeeded:: New encumbrance id={} from fund id={}", newEncumbrance.getId(), newEncumbrance.getFromFundId());
        var oldId = payment.getPaymentEncumbranceId();
        payment.setPaymentEncumbranceId(newEncumbrance.getId());
        transactions.add(payment);
        logger.info("relinkPaymentsIfNeeded:: Updated payment encumbranceId from={} to={}", oldId, payment.getPaymentEncumbranceId());
      }
    });
  }

  private boolean isValidAnalogousEncumbrance(Transaction tr, Transaction oldEncumbrance) {
    return !StringUtils.equals(tr.getFromFundId(), oldEncumbrance.getFromFundId())
      && StringUtils.equals(tr.getFiscalYearId(), oldEncumbrance.getFiscalYearId())
      && StringUtils.equals(tr.getEncumbrance().getSourcePoLineId(), oldEncumbrance.getEncumbrance().getSourcePoLineId())
      && Objects.equals(tr.getAmount(), oldEncumbrance.getAmount())
      && Objects.equals(tr.getEncumbrance().getStatus(), oldEncumbrance.getEncumbrance().getStatus());
  }

  private Future<Void> deleteTransactions(BatchTransactionHolder holder, DBConn conn) {
    List<String> ids = holder.getIdsOfTransactionsToDelete();
    if (ids.isEmpty()) {
      return succeededFuture();
    }
    return transactionDAO.deleteTransactionsByIds(ids, conn)
      .onSuccess(v -> logger.info("Batch transactions: successfully deleted {} transactions", ids.size()))
      .onFailure(t -> logger.error("Batch transactions: failed to delete transactions, ids = {}", ids, t));
  }

  private Future<Void> updateBudgets(BatchTransactionHolder holder, DBConn conn) {
    List<Budget> budgets = holder.getBudgets();
    if (budgets.isEmpty()) {
      return succeededFuture();
    }
    return budgetService.updateBatchBudgets(budgets, conn, true)
      .onSuccess(v -> logger.info("Batch transactions: successfully updated {} budgets", budgets.size()))
      .onFailure(t -> logger.error("Batch transactions: failed to update budgets, budgets = {}",
        Json.encode(budgets), t))
      .mapEmpty();
  }

  private List<Transaction> getByType(TransactionType trType, List<Transaction> transactions) {
    return transactions.stream()
      .filter(tr -> tr.getTransactionType() == trType || (tr.getTransactionType() == CREDIT && trType == PAYMENT))
      .toList();
  }

  private BatchTransactionServiceInterface getBatchServiceForType(TransactionType trType) {
    return serviceMap.get(trType == CREDIT ? PAYMENT : trType);
  }
}
