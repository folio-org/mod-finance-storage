package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.summary.InvoiceTransactionSummaryDAO;
import org.folio.dao.summary.OrderTransactionSummaryDAO;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.dao.transactions.TemporaryInvoiceTransactionDAO;
import org.folio.dao.transactions.TemporaryOrderTransactionDAO;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.okapi.common.OkapiToken;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.rest.jaxrs.model.TransactionPatch;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.CREDIT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;

public class BatchTransactionService {
  private static final Logger logger = LogManager.getLogger();
  private static final List<TransactionType> TYPES_WITH_SUMMARIES = List.of(ENCUMBRANCE, PENDING_PAYMENT, PAYMENT, CREDIT);
  private final DBClientFactory dbClientFactory;
  private final TransactionManagingStrategyFactory managingServiceFactory;
  private final TransactionService defaultTransactionService;
  private final OrderTransactionSummaryDAO orderTransactionSummaryDAO;
  private final InvoiceTransactionSummaryDAO invoiceTransactionSummaryDAO;
  private final TemporaryOrderTransactionDAO temporaryOrderTransactionDAO;
  private final TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO;

  public BatchTransactionService(DBClientFactory dbClientFactory, TransactionManagingStrategyFactory managingServiceFactory,
      TransactionService defaultTransactionService, OrderTransactionSummaryDAO orderTransactionSummaryDAO,
      InvoiceTransactionSummaryDAO invoiceTransactionSummaryDAO, TemporaryOrderTransactionDAO temporaryOrderTransactionDAO,
      TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO) {
    this.dbClientFactory = dbClientFactory;
    this.managingServiceFactory = managingServiceFactory;
    this.defaultTransactionService = defaultTransactionService;
    this.orderTransactionSummaryDAO = orderTransactionSummaryDAO;
    this.invoiceTransactionSummaryDAO = invoiceTransactionSummaryDAO;
    this.temporaryOrderTransactionDAO = temporaryOrderTransactionDAO;
    this.temporaryInvoiceTransactionDAO = temporaryInvoiceTransactionDAO;
  }

  public Future<Void> processBatch(Batch batch, RequestContext requestContext) {
    try {
      sanityChecks(batch);
    } catch (Exception ex) {
      logger.error(ex);
      return Future.failedFuture(ex);
    }
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return client.withTrans(conn -> createTransactions(batch.getTransactionsToCreate(), conn, requestContext.getHeaders())
      .compose(v -> updateTransactions(batch.getTransactionsToUpdate(), conn))
      .compose(v -> deleteTransactions(batch.getIdsOfTransactionsToDelete(), conn))
      .compose(v -> patchTransactions(batch.getTransactionPatches(), conn))
    ).onSuccess(v -> logger.info("All batch transaction operations were successful."))
      .onFailure(t -> logger.error("Error when batch processing transactions", t));
  }

  private void sanityChecks(Batch batch) {
    checkIdIsPresent(batch.getTransactionsToCreate(), "create");
    checkIdIsPresent(batch.getTransactionsToUpdate(), "update");
    // ids of patches are already checked with the schema
    if (batch.getTransactionsToCreate().isEmpty() && batch.getTransactionsToUpdate().isEmpty() &&
        batch.getIdsOfTransactionsToDelete().isEmpty() && batch.getTransactionPatches().isEmpty()) {
      throw new HttpException(400, "At least one of the batch operations needs to be used.");
    }
  }

  private void checkIdIsPresent(List<Transaction> transactions, String operation) {
    for (Transaction transaction : transactions) {
      if (StringUtils.isBlank(transaction.getId())) {
        throw new HttpException(400, String.format("Id is required in transactions to %s.", operation));
      }
      if (TYPES_WITH_SUMMARIES.contains(transaction.getTransactionType()) && transaction.getSourceInvoiceId() == null &&
          (transaction.getEncumbrance() == null || transaction.getEncumbrance().getSourcePurchaseOrderId() == null)) {
        throw new HttpException(400, String.format("Missing invoice id or order id in transaction %s", transaction.getId()));
      }
    }
  }

  private Future<Void> createTransactions(List<Transaction> transactions, DBConn conn,
      Map<String, String> okapiHeaders) {
    if (transactions.isEmpty())
      return Future.succeededFuture();
    populateMetadata(transactions, okapiHeaders);
    return createOrUpdate(transactions,
        (transactionService, transaction) -> transactionService.createTransaction(transaction, conn).mapEmpty(), conn)
      .onSuccess(v -> logger.info("Batch transactions: successfully created transactions"))
      .onFailure(t -> logger.error("Batch transactions: failed to create transactions", t));
  }

  private Future<Void> updateTransactions(List<Transaction> transactions, DBConn conn) {
    if (transactions.isEmpty())
      return Future.succeededFuture();
    return createOrUpdate(transactions,
        (transactionService, transaction) -> transactionService.updateTransaction(transaction, conn), conn)
      .onSuccess(v -> logger.info("Batch transactions: successfully updated transactions"))
      .onFailure(t -> logger.error("Batch transactions: failed to update transactions", t));
  }

  private Future<Void> deleteTransactions(List<String> ids, DBConn conn) {
    Future<Void> f = Future.succeededFuture();
    for (String id : ids) {
      f = f.compose(v -> defaultTransactionService.deleteTransactionById(id, conn));
    }
    return f.onSuccess(v -> logger.info("Batch transactions: successfully deleted transactions"))
      .onFailure(t -> logger.error("Batch transactions: failed to delete transactions", t));
  }

  private Future<Void> patchTransactions(List<TransactionPatch> patches, DBConn conn) {
    if (patches.isEmpty())
      return Future.succeededFuture();
    return Future.failedFuture(new HttpException(500, "transactionPatches: not implemented"));
  }

  private void populateMetadata(List<Transaction> transactions, Map<String, String> okapiHeaders) {
    // NOTE: Okapi usually populates metadata when a POST method is used with an entity with metadata.
    // But in the case of the batch API there is no top-level metadata, so it is not populated automatically.
    for (Transaction tr : transactions) {
      if (tr.getMetadata() == null) {
        String userId = okapiHeaders.get("X-Okapi-User-Id");
        if (userId == null) {
          try {
            userId = (new OkapiToken(okapiHeaders.get("X-Okapi-Token"))).getUserIdWithoutValidation();
          } catch (Exception ignored) {
            // could not find user id - ignoring
          }
        }
        Metadata md = new Metadata();
        md.setUpdatedDate(new Date());
        md.setCreatedDate(md.getUpdatedDate());
        md.setCreatedByUserId(userId);
        md.setUpdatedByUserId(userId);
        tr.setMetadata(md);
      }
    }
  }

  private Future<Void> createOrUpdate(List<Transaction> transactions,
      BiFunction<TransactionService, Transaction, Future<Void>> createOrUpdateFct, DBConn conn) {
    // Futures have to be executed in order, one after the other.
    // Using functions instead of futures to avoid starting the futures before they are composed.
    List<Function<Void, Future<Void>>> functions = new ArrayList<>();
    groupByType(transactions).forEach((transactionType, transactionsForType) -> {
      TransactionService transactionService = managingServiceFactory.findStrategy(transactionType);
      groupBySummaryId(transactionsForType).forEach((summaryId, transactionsForSummary) -> {
        if (TYPES_WITH_SUMMARIES.contains(transactionType)) {
          functions.add(v -> createOrUpdateSummary(summaryId, transactionsForSummary, transactionType, conn));
          functions.add(v -> removeTemporaryTransactions(summaryId, transactionType, conn));
        }
        transactionsForSummary.forEach(tr -> functions.add(v -> createOrUpdateFct.apply(transactionService, tr)));
      });
    });
    Future<Void> future = Future.succeededFuture();
    for (Function<Void, Future<Void>> fct : functions) {
      future = future.compose(v -> fct.apply(null));
    }
    return future;
  }

  private Map<TransactionType, List<Transaction>> groupByType(List<Transaction> transactions) {
    return transactions.stream().collect(groupingBy(tr -> tr.getTransactionType() == TransactionType.CREDIT ?
      TransactionType.PAYMENT : tr.getTransactionType()));
  }

  private Map<String, List<Transaction>> groupBySummaryId(List<Transaction> transactions) {
    return transactions.stream().collect(groupingBy(tr -> {
      if (tr.getSourceInvoiceId() != null) {
        return tr.getSourceInvoiceId();
      } else if (tr.getEncumbrance() != null) {
        return tr.getEncumbrance().getSourcePurchaseOrderId();
      } else {
        return "no summary id";
      }
    }));
  }

  private Future<Void> createOrUpdateSummary(String summaryId, List<Transaction> transactions,
      TransactionType transactionType, DBConn conn) {
    TransactionSummaryDao dao;
    Object summary;
    if (transactionType == TransactionType.ENCUMBRANCE) {
      summary = new OrderTransactionSummary().withId(summaryId).withNumTransactions(transactions.size());
      dao = orderTransactionSummaryDAO;
    } else {
      summary = new InvoiceTransactionSummary()
        .withId(summaryId)
        .withNumPaymentsCredits(transactions.size())
        .withNumPendingPayments(transactionType == TransactionType.PENDING_PAYMENT ? transactions.size() : 0);
      dao = invoiceTransactionSummaryDAO;
    }
    return dao.getSummaryById(summaryId, conn)
      .recover(t -> {
        if (t instanceof HttpException he && he.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
          return Future.succeededFuture(null);
        }
        return Future.failedFuture(t);
      })
      .compose(obj -> {
        if (obj == null) {
          return dao.createSummary(JsonObject.mapFrom(summary), conn);
        } else {
          return dao.updateSummary(JsonObject.mapFrom(summary), conn);
        }
      });
  }

  private Future<Void> removeTemporaryTransactions(String summaryId, TransactionType transactionType, DBConn conn) {
    TemporaryTransactionDAO temporaryTransactionDAO;
    if (transactionType == ENCUMBRANCE) {
      temporaryTransactionDAO = temporaryOrderTransactionDAO;
    } else {
      temporaryTransactionDAO = temporaryInvoiceTransactionDAO;
    }
    return temporaryTransactionDAO.deleteTempTransactions(summaryId, conn)
      .mapEmpty();
  }
}
