package org.folio.service.summary;

import io.vertx.core.json.JsonObject;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;

import java.util.Optional;

public class EncumbranceTransactionSummaryService extends AbstractTransactionSummaryService {

  public EncumbranceTransactionSummaryService(TransactionSummaryDao transactionSummaryDao) {
    super(transactionSummaryDao);
  }

  @Override
  public String getSummaryId(Transaction transaction) {
    return Optional.ofNullable(transaction.getEncumbrance())
      .map(Encumbrance::getSourcePurchaseOrderId)
      .orElse(null);
  }

  @Override
  protected boolean isProcessed(JsonObject summary) {
    return getNumTransactions(summary) < 0;
  }

  @Override
  protected void setTransactionsSummariesProcessed(JsonObject summary) {
    summary.put("numTransactions", -Math.abs(getNumTransactions(summary)));
  }

  @Override
  public Integer getNumTransactions(JsonObject summary) {
    return summary.getInteger("numTransactions");
  }

}
