package org.folio.service.transactions.batch;

import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.okapi.common.OkapiToken;
import org.folio.okapi.common.XOkapiHeaders;
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

import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.CREDIT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.TRANSFER;

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
    try {
      BatchTransactionChecks.sanityChecks(batch);
    } catch (Exception ex) {
      logger.error("Sanity checks before processing batch transactions failed", ex);
      return Future.failedFuture(ex);
    }
    populateMetadata(batch, requestContext.getHeaders());
    DBClient client = dbClientFactory.getDbClient(requestContext);
    BatchTransactionHolder holder = new BatchTransactionHolder(transactionDAO, fundService, budgetService, ledgerService);
    return client.withTrans(conn -> holder.setup(batch, conn)
      .map(v -> {
        BatchTransactionChecks.checkBudgetsAreActive(holder);
        prepareCreatingTransactions(holder);
        prepareUpdatingTransactions(holder);
        preparePatchingTransactions(holder);
        BatchTransactionChecks.checkRestrictedBudgets(holder);
        return null;
      })
      .compose(v -> applyChanges(holder, conn))
    ).onSuccess(v -> logger.info("All batch transaction operations were successful."))
      .onFailure(t -> logger.error("Error when batch processing transactions, batch={}",
        JsonObject.mapFrom(batch).encodePrettily(), t));
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
    return transactionDAO.updateTransactions(transactions, conn)
      .onSuccess(v -> logger.info("Batch transactions: successfully updated {} transactions", transactions.size()))
      .onFailure(t -> logger.error("Batch transactions: failed to update transactions, transactions = {}",
        Json.encode(transactions), t));
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
    return budgetService.updateBatchBudgets(budgets, conn)
      .onSuccess(v -> logger.info("Batch transactions: successfully updated {} budgets", budgets.size()))
      .onFailure(t -> logger.error("Batch transactions: failed to update budgets, budgets = {}",
        Json.encode(budgets), t))
      .mapEmpty();
  }

  private void populateMetadata(Batch batch, Map<String, String> okapiHeaders) {
    // NOTE: Okapi usually populates metadata when a POST method is used with an entity with metadata.
    // But in the case of the batch API there is no top-level metadata, so it is not populated automatically.
    String userId = okapiHeaders.get(XOkapiHeaders.USER_ID);
    if (userId == null) {
      try {
        userId = (new OkapiToken(okapiHeaders.get(XOkapiHeaders.TOKEN))).getUserIdWithoutValidation();
      } catch (Exception ignored) {
        // could not find user id - ignoring
      }
    }
    for (Transaction tr : batch.getTransactionsToCreate()) {
      if (tr.getMetadata() == null) {
        Metadata md = new Metadata();
        md.setUpdatedDate(new Date());
        md.setCreatedDate(md.getUpdatedDate());
        md.setCreatedByUserId(userId);
        md.setUpdatedByUserId(userId);
        tr.setMetadata(md);
      }
    }
    for (Transaction tr : batch.getTransactionsToUpdate()) {
      Metadata md = tr.getMetadata();
      md.setUpdatedDate(new Date());
      md.setUpdatedByUserId(userId);
    }
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
