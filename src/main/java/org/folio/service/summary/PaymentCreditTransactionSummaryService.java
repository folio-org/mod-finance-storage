package org.folio.service.summary;

import io.vertx.core.json.JsonObject;
import org.folio.dao.summary.TransactionSummaryDao;

public class PaymentCreditTransactionSummaryService extends AbstractTransactionSummaryService {

  public PaymentCreditTransactionSummaryService(TransactionSummaryDao transactionSummaryDao) {
    super(transactionSummaryDao);
  }

  @Override
  protected boolean isProcessed(JsonObject summary) {
    return getNumTransactions(summary) < 0;
  }

  @Override
  protected void setTransactionsSummariesProcessed(JsonObject summary) {
    summary.put("numPaymentsCredits", -getNumTransactions(summary));
  }

  @Override
  public Integer getNumTransactions(JsonObject summary) {
    return summary.getInteger("numPaymentsCredits");
  }

}
