package org.folio.config;


import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.budget.BudgetPostgresDAO;
import org.folio.dao.fund.FundDAO;
import org.folio.dao.fund.FundPostgresDAO;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.dao.ledger.LedgerPostgresDAO;
import org.folio.dao.ledgerfy.LedgerFiscalYearDAO;
import org.folio.dao.ledgerfy.LedgerFiscalYearPostgresDAO;
import org.folio.dao.summary.InvoiceTransactionSummaryDAO;
import org.folio.dao.summary.OrderTransactionSummaryDAO;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.dao.transactions.EncumbranceDAO;
import org.folio.dao.transactions.PaymentCreditDAO;
import org.folio.dao.transactions.PendingPaymentDAO;
import org.folio.dao.transactions.TemporaryInvoiceTransactionDAO;
import org.folio.dao.transactions.TemporaryOrderTransactionDAO;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.fund.StorageFundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledger.StorageLedgerService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.folio.service.ledgerfy.StorageLedgerFiscalYearService;
import org.folio.service.summary.EncumbranceTransactionSummaryService;
import org.folio.service.summary.PaymentCreditTransactionSummaryService;
import org.folio.service.summary.PendingPaymentTransactionSummaryService;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.DefaultTransactionService;
import org.folio.service.transactions.EncumbranceAllOrNothingService;
import org.folio.service.transactions.PaymentCreditAllOrNothingService;
import org.folio.service.transactions.PendingPaymentAllOrNothingService;
import org.folio.service.transactions.TransactionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

  @Bean
  public BudgetDAO budgetDAO() {
    return new BudgetPostgresDAO();
  }

  @Bean
  public FundDAO fundDAO() {
    return new FundPostgresDAO();
  }

  @Bean
  public LedgerDAO ledgerDAO() {
    return new LedgerPostgresDAO();
  }

  @Bean
  public LedgerFiscalYearDAO ledgerFiscalYearDAO() {
    return new LedgerFiscalYearPostgresDAO();
  }

  @Bean
  public TransactionSummaryDao<InvoiceTransactionSummary> invoiceTransactionSummaryDao() {
    return new InvoiceTransactionSummaryDAO();
  }

  @Bean
  public TransactionSummaryDao<OrderTransactionSummary> orderTransactionSummaryDao() {
    return new OrderTransactionSummaryDAO();
  }

  @Bean
  public TemporaryTransactionDAO temporaryInvoiceTransactionDAO() {
    return new TemporaryInvoiceTransactionDAO();
  }

  @Bean
  public TemporaryTransactionDAO temporaryOrderTransactionDAO() {
    return new TemporaryOrderTransactionDAO();
  }

  @Bean
  public TransactionDAO encumbranceDAO() {
    return new EncumbranceDAO();
  }

  @Bean
  public TransactionDAO paymentCreditDAO() {
    return new PaymentCreditDAO();
  }

  @Bean
  public TransactionDAO pendingPaymentDAO() {
    return new PendingPaymentDAO();
  }

  @Bean
  public BudgetService budgetService(BudgetDAO budgetDAO) {
    return new BudgetService(budgetDAO);
  }

  @Bean
  public FundService fundService(FundDAO fundDAO) {
    return new StorageFundService(fundDAO);
  }

  @Bean
  public LedgerService ledgerService(LedgerDAO ledgerDAO, FundService fundService) {
    return new StorageLedgerService(ledgerDAO, fundService);
  }

  @Bean
  public LedgerFiscalYearService ledgerFiscalYearService(LedgerFiscalYearDAO ledgerFiscalYearDAO, FundService fundService) {
    return new StorageLedgerFiscalYearService(ledgerFiscalYearDAO, fundService);
  }

  @Bean
  public TransactionSummaryService<OrderTransactionSummary> encumbranceSummaryService(TransactionSummaryDao<OrderTransactionSummary> orderTransactionSummaryDao) {
    return new EncumbranceTransactionSummaryService(orderTransactionSummaryDao);
  }

  @Bean
  public TransactionSummaryService<InvoiceTransactionSummary> paymentCreditSummaryService(TransactionSummaryDao<InvoiceTransactionSummary> invoiceTransactionSummaryDao) {
    return new PaymentCreditTransactionSummaryService(invoiceTransactionSummaryDao);
  }

  @Bean
  public TransactionSummaryService<InvoiceTransactionSummary> pendingPaymentSummaryService(TransactionSummaryDao<InvoiceTransactionSummary> invoiceTransactionSummaryDao) {
    return new PendingPaymentTransactionSummaryService(invoiceTransactionSummaryDao);
  }

  @Bean
  public TransactionService pendingPaymentService(BudgetService budgetService,
                                                  TemporaryTransactionDAO temporaryInvoiceTransactionDAO,
                                                  LedgerFiscalYearService ledgerFiscalYearService,
                                                  FundService fundService,
                                                  @Qualifier("pendingPaymentSummaryService")TransactionSummaryService<InvoiceTransactionSummary> summaryService,
                                                  TransactionDAO pendingPaymentDAO,
                                                  LedgerService ledgerService) {

    return new PendingPaymentAllOrNothingService(budgetService, temporaryInvoiceTransactionDAO,
      ledgerFiscalYearService, fundService, summaryService, pendingPaymentDAO, ledgerService);
  }

  @Bean
  public TransactionService paymentCreditService(BudgetService budgetService,
                                                  TemporaryTransactionDAO temporaryInvoiceTransactionDAO,
                                                  LedgerFiscalYearService ledgerFiscalYearService,
                                                  FundService fundService,
                                                  @Qualifier("paymentCreditSummaryService") TransactionSummaryService<InvoiceTransactionSummary> summaryService,
                                                  TransactionDAO paymentCreditDAO,
                                                  LedgerService ledgerService) {

    return new PaymentCreditAllOrNothingService(budgetService, temporaryInvoiceTransactionDAO,
      ledgerFiscalYearService, fundService, summaryService, paymentCreditDAO, ledgerService);
  }

  @Bean
  public TransactionService encumbranceService(BudgetService budgetService,
                                                 TemporaryTransactionDAO temporaryOrderTransactionDAO,
                                                 LedgerFiscalYearService ledgerFiscalYearService,
                                                 FundService fundService,
                                                 TransactionSummaryService<OrderTransactionSummary> encumbranceSummaryService,
                                                 TransactionDAO encumbranceDAO,
                                                 LedgerService ledgerService) {

    return new EncumbranceAllOrNothingService(budgetService, temporaryOrderTransactionDAO,
      ledgerFiscalYearService, fundService, encumbranceSummaryService, encumbranceDAO, ledgerService);
  }

  @Bean
  public TransactionService defaultTransactionService() {
    return new DefaultTransactionService();
  }

}
