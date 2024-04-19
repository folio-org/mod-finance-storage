package org.folio.service.transactions.batch;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
import org.javamoney.moneta.Money;

import javax.money.MonetaryAmount;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static org.folio.rest.jaxrs.model.Budget.BudgetStatus.ACTIVE;
import static org.folio.rest.jaxrs.model.Budget.BudgetStatus.PLANNED;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.CREDIT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.TRANSFER;
import static org.folio.rest.util.ErrorCodes.ALLOCATION_MUST_BE_POSITIVE;
import static org.folio.rest.util.ErrorCodes.BUDGET_IS_NOT_ACTIVE_OR_PLANNED;
import static org.folio.rest.util.ErrorCodes.BUDGET_RESTRICTED_ENCUMBRANCE_ERROR;
import static org.folio.rest.util.ErrorCodes.BUDGET_RESTRICTED_EXPENDITURES_ERROR;
import static org.folio.rest.util.ErrorCodes.ID_IS_REQUIRED_IN_TRANSACTIONS;
import static org.folio.rest.util.ErrorCodes.MISSING_FUND_ID;
import static org.folio.rest.util.ErrorCodes.PAYMENT_OR_CREDIT_HAS_NEGATIVE_AMOUNT;

public class BatchTransactionChecks {
  private static final Logger logger = LogManager.getLogger();

  // Private constructor because there are only static methods, so we never need to create instances of this class.
  private BatchTransactionChecks() {
  }

  public static void sanityChecks(Batch batch) {
    // NOTE: This is just for checks that don't require database access, there are others after data is loaded
    if (batch.getTransactionsToCreate().isEmpty() && batch.getTransactionsToUpdate().isEmpty() &&
      batch.getIdsOfTransactionsToDelete().isEmpty() && batch.getTransactionPatches().isEmpty()) {
      throw new HttpException(400, "At least one of the batch operations needs to be used.");
    }
    checkIdIsPresent(batch.getTransactionsToCreate(), "create");
    checkIdIsPresent(batch.getTransactionsToUpdate(), "update");
    // ids of patches are already checked with the schema
    checkSingleUpdates(batch.getTransactionsToCreate(), "create");
    checkSingleUpdates(batch.getTransactionsToUpdate(), "update");
    checkTransactionsToUpdateHaveMetadata(batch.getTransactionsToUpdate());
    batch.getTransactionsToCreate()
      .forEach(transaction -> {
        if (transaction.getTransactionType() == ALLOCATION) {
          checkAllocation(transaction);
        } else if (transaction.getTransactionType() == TRANSFER) {
          checkTransfer(transaction);
        }
      });
    checkPaymentsAndCreditsAmounts(batch);
  }

  public static void checkBudgetsAreActive(BatchTransactionHolder holder) {
    holder.getBudgets().forEach(budget -> {
      if (!List.of(ACTIVE, PLANNED).contains(budget.getBudgetStatus())) {
        Error error = BUDGET_IS_NOT_ACTIVE_OR_PLANNED.toError();
        Parameter fundCodeParam = new Parameter().withKey("fundCode").withValue(holder.getFundCodeForBudget(budget));
        error.setParameters(List.of(fundCodeParam));
        throw new HttpException(400, error);
      }
    });
  }

  public static Future<Void> checkTransactionsToDelete(Set<String> idsOfTransactionsToDelete,
      BatchTransactionDAO transactionDAO, DBConn conn) {
    // There is currently no budget update when a transaction is deleted.
    // Batch delete should only be used to delete released encumbrances.
    if (idsOfTransactionsToDelete.isEmpty()) {
      return succeededFuture();
    }
    return transactionDAO.getTransactionsByIds(idsOfTransactionsToDelete.stream().toList(), conn)
      .map(transactionsToDelete -> {
        if (transactionsToDelete.size() != idsOfTransactionsToDelete.size()) {
          throw new HttpException(400, "One or more transaction to delete was not found");
        }
        // NOTE: for the following checks we can't throw an exception because it would prevent the encumbrance script
        // from working, so instead we just log a warning.
        // Also in the future we could support auto-releasing encumbrances to delete, but this would not work well
        // when there are duplicate encumbrances (released/unreleased), so we don't do it yet.
        // Also note that the check for connected invoices is also done in mod-finance, and it throws an exception there
        // (the encumbrance script is using mod-finance-storage directly).
        transactionsToDelete.forEach(tr -> {
          if (tr.getTransactionType() != ENCUMBRANCE || tr.getEncumbrance().getStatus() != Encumbrance.Status.RELEASED) {
            logger.warn("A transaction to delete is not a released encumbrance, id={}", tr.getId());
          }
        });
        return transactionsToDelete.stream()
          .filter(tr -> tr.getTransactionType() == ENCUMBRANCE)
          .map(Transaction::getId)
          .toList();
      })
      .compose(idsOfEncumbrancesToDelete -> {
        if (idsOfEncumbrancesToDelete.isEmpty()) {
          return succeededFuture(emptyList());
        }
        CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
        idsOfEncumbrancesToDelete.forEach(id -> criterionBuilder.withJson("awaitingPayment.encumbranceId", "=", id));
        return transactionDAO.getTransactionsByCriterion(criterionBuilder.build(), conn);
      })
      .map(pendingPayments -> {
        if (!pendingPayments.isEmpty()) {
          logger.warn("An invoice is connected to an encumbrance to delete, id={}", pendingPayments.get(0).getId());
        }
        return null;
      });
  }

  public static void checkExistingTransactionsConsistency(List<Transaction> allTransactionsToCreate,
      List<Transaction> allTransactionsToUpdate, Map<String, Transaction>  existingTransactionMap) {
    allTransactionsToCreate.forEach(tr -> {
      if (existingTransactionMap.containsKey(tr.getId())) {
        throw new HttpException(400, String.format("A transaction to create already exists: %s", tr.getId()));
      }
    });
    allTransactionsToUpdate.forEach(tr -> {
      if (!existingTransactionMap.containsKey(tr.getId())) {
        throw new HttpException(400, String.format("A transaction to update does not exist: %s", tr.getId()));
      }
    });
  }

  public static void checkRestrictedBudgets(BatchTransactionHolder holder) {
    List<Budget> budgets = holder.getBudgets();
    if (budgets.isEmpty()) {
      return;
    }
    String currency = holder.getCurrency();
    budgets.forEach(budget -> {
      if (holder.budgetExpendituresAreRestricted(budget.getId())) {
        checkRestrictedExpenditures(budget, currency, holder);
      }
      if (holder.budgetEncumbranceIsRestricted(budget.getId())) {
        checkRestrictedEncumbrance(budget, currency, holder);
      }
    });
  }

  private static void checkIdIsPresent(List<Transaction> transactions, String operation) {
    for (Transaction transaction : transactions) {
      if (transaction.getId() == null) {
        Error error = ID_IS_REQUIRED_IN_TRANSACTIONS.toError();
        error.setMessage(MessageFormat.format(error.getMessage(), operation));
        throw new HttpException(400, error);
      }
      if (ENCUMBRANCE == transaction.getTransactionType() && (transaction.getEncumbrance() == null ||
          transaction.getEncumbrance().getSourcePurchaseOrderId() == null)) {
        throw new HttpException(400, String.format("Missing order id in encumbrance %s", transaction.getId()));
      }
      if (List.of(PENDING_PAYMENT, PAYMENT, CREDIT).contains(transaction.getTransactionType()) &&
          transaction.getSourceInvoiceId() == null) {
        throw new HttpException(400, String.format("Missing invoice id in transaction %s", transaction.getId()));
      }
    }
  }

  private static void checkSingleUpdates(List<Transaction> transactions, String operation) {
    List<String> distinctIds = transactions.stream().map(Transaction::getId).distinct().toList();
    if (distinctIds.size() != transactions.size()) {
      List<String> ids = transactions.stream().map(Transaction::getId).toList();
      Set<String> duplicates = ids.stream().filter(id -> Collections.frequency(ids, id) > 1).collect(Collectors.toSet());
      throw new HttpException(400,
        String.format("At least one transaction is present twice in transactions to %s, duplicates: %s", operation, duplicates));
    }
  }

  private static void checkTransactionsToUpdateHaveMetadata(List<Transaction> transactions) {
    transactions.forEach(tr -> {
      if (tr.getMetadata() == null) {
        throw new HttpException(400, String.format("At least one transaction to update does not have metadata, id=%s", tr.getId()));
      }
    });
  }

  private static void checkAllocation(Transaction allocation) {
    if (allocation.getAmount() <= 0) {
      List<Parameter> parameters = List.of(new Parameter().withKey("fieldName").withValue("amount"));
      Error error = ALLOCATION_MUST_BE_POSITIVE.toError().withParameters(parameters);
      throw new HttpException(400, error);
    }
    if (allocation.getToFundId() == null && allocation.getFromFundId() == null) {
      throw new HttpException(400, MISSING_FUND_ID.toError());
    }
  }

  private static void checkTransfer(Transaction transfer) {
    if (transfer.getToFundId() == null) {
      throw new HttpException(400, String.format("A transfer with id %s has a null toFundId",
        transfer.getId()));
    }
  }

  private static void checkRestrictedExpenditures(Budget budget, String currency, BatchTransactionHolder holder) {
    // [remaining amount] = (allocated + netTransfers) * allowableExpenditure - (awaitingPayment + expended)
    MonetaryAmount allocated = Money.of(budget.getAllocated(), currency);
    // get allowableExpenditure from percentage value
    double allowableExpenditure = Money.of(budget.getAllowableExpenditure(), currency).divide(100d)
      .getNumber().doubleValue();

    MonetaryAmount expended = Money.of(budget.getExpenditures(), currency);
    MonetaryAmount awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    MonetaryAmount netTransfers = Money.of(budget.getNetTransfers(), currency);

    MonetaryAmount totalFunding = allocated.add(netTransfers);
    MonetaryAmount unavailable = awaitingPayment.add(expended);

    double remaining = totalFunding.multiply(allowableExpenditure).subtract(unavailable).getNumber().doubleValue();
    if (remaining < 0) {
      List<Parameter> parameters = new ArrayList<>();
      parameters.add(new Parameter().withKey("fundCode").withValue(holder.getFundCodeForBudget(budget)));
      parameters.add(new Parameter().withKey("budgetId").withValue(budget.getId()));
      parameters.add(new Parameter().withKey("remaining").withValue(Double.toString(remaining)));
      parameters.add(new Parameter().withKey("allocated").withValue(Double.toString(allocated.getNumber().doubleValue())));
      parameters.add(new Parameter().withKey("netTransfers").withValue(Double.toString(netTransfers.getNumber().doubleValue())));
      parameters.add(new Parameter().withKey("allowableExpenditure").withValue(Double.toString(allowableExpenditure)));
      parameters.add(new Parameter().withKey("awaitingPayment").withValue(Double.toString(awaitingPayment.getNumber().doubleValue())));
      parameters.add(new Parameter().withKey("expended").withValue(Double.toString(expended.getNumber().doubleValue())));
      Error error = BUDGET_RESTRICTED_EXPENDITURES_ERROR.toError().withParameters(parameters);
      throw new HttpException(422, error);
    }
  }

  private static void checkRestrictedEncumbrance(Budget budget, String currency, BatchTransactionHolder holder) {
    // [remaining amount] = (allocated + netTransfers) * allowableEncumbered - (encumbered + awaitingPayment + expended)
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableEncumbered converted from percentage value
    double allowableEncumbered = Money.of(budget.getAllowableEncumbrance(), currency).divide(100d).getNumber().doubleValue();
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    Money expended = Money.of(budget.getExpenditures(), currency);
    Money netTransfers = Money.of(budget.getNetTransfers(), currency);

    Money totalFunding = allocated.add(netTransfers);
    Money unavailable = encumbered.add(awaitingPayment).add(expended);

    double remaining = totalFunding.multiply(allowableEncumbered).subtract(unavailable).getNumber().doubleValue();
    if (remaining < 0) {
      List<Parameter> parameters = new ArrayList<>();
      parameters.add(new Parameter().withKey("fundCode").withValue(holder.getFundCodeForBudget(budget)));
      parameters.add(new Parameter().withKey("budgetId").withValue(budget.getId()));
      parameters.add(new Parameter().withKey("remaining").withValue(Double.toString(remaining)));
      parameters.add(new Parameter().withKey("allocated").withValue(Double.toString(allocated.getNumber().doubleValue())));
      parameters.add(new Parameter().withKey("netTransfers").withValue(Double.toString(netTransfers.getNumber().doubleValue())));
      parameters.add(new Parameter().withKey("allowableEncumbered").withValue(Double.toString(allowableEncumbered)));
      parameters.add(new Parameter().withKey("encumbered").withValue(Double.toString(encumbered.getNumber().doubleValue())));
      parameters.add(new Parameter().withKey("awaitingPayment").withValue(Double.toString(awaitingPayment.getNumber().doubleValue())));
      parameters.add(new Parameter().withKey("expended").withValue(Double.toString(expended.getNumber().doubleValue())));
      Error error = BUDGET_RESTRICTED_ENCUMBRANCE_ERROR.toError().withParameters(parameters);
      throw new HttpException(422, error);
    }
  }

  private static void checkPaymentsAndCreditsAmounts(Batch batch) {
    List<Transaction> newPaymentsAndCredits = batch.getTransactionsToCreate().stream()
      .filter(tr -> List.of(PAYMENT, CREDIT).contains(tr.getTransactionType()))
      .toList();
    for (Transaction tr : newPaymentsAndCredits) {
      if (tr.getAmount() < 0) {
        List<Parameter> parameters = List.of(new Parameter().withKey("id").withValue(tr.getId()));
        Error error = PAYMENT_OR_CREDIT_HAS_NEGATIVE_AMOUNT.toError().withParameters(parameters);
        throw new HttpException(422, error);
      }
    }
  }
}
