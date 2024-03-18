package org.folio.config;

import java.util.Set;

import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.budget.BudgetExpenseClassDAO;
import org.folio.dao.expense.ExpenseClassDAO;
import org.folio.dao.fiscalyear.FiscalYearDAO;
import org.folio.dao.fund.FundDAO;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.dao.rollover.RolloverBudgetDAO;
import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.dao.transactions.TemporaryInvoiceTransactionDAO;
import org.folio.dao.transactions.TemporaryOrderTransactionDAO;
import org.folio.dao.transactions.TemporaryEncumbranceTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetExpenseClassService;
import org.folio.service.budget.BudgetService;
import org.folio.service.budget.RolloverBudgetExpenseClassTotalsService;
import org.folio.service.email.EmailService;
import org.folio.service.fiscalyear.FiscalYearService;
import org.folio.service.fund.FundService;
import org.folio.service.fund.StorageFundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledger.StorageLedgerService;
import org.folio.service.rollover.LedgerRolloverService;
import org.folio.service.rollover.RolloverBudgetService;
import org.folio.service.rollover.RolloverErrorService;
import org.folio.service.rollover.RolloverProgressService;
import org.folio.service.rollover.RolloverValidationService;
import org.folio.service.summary.EncumbranceTransactionSummaryService;
import org.folio.service.summary.PaymentCreditTransactionSummaryService;
import org.folio.service.summary.PendingPaymentTransactionSummaryService;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.AllOrNothingTransactionService;
import org.folio.service.transactions.AllocationService;
import org.folio.service.transactions.DefaultTransactionService;
import org.folio.service.transactions.EncumbranceService;
import org.folio.service.transactions.PaymentCreditService;
import org.folio.service.transactions.PendingPaymentService;
import org.folio.service.transactions.TransactionManagingStrategy;
import org.folio.service.transactions.TransactionManagingStrategyFactory;
import org.folio.service.transactions.TransactionService;
import org.folio.service.transactions.TemporaryTransactionService;
import org.folio.service.transactions.TransferService;
import org.folio.service.transactions.batch.BatchAllocationService;
import org.folio.service.transactions.batch.BatchEncumbranceService;
import org.folio.service.transactions.batch.BatchPaymentCreditService;
import org.folio.service.transactions.batch.BatchPendingPaymentService;
import org.folio.service.transactions.batch.BatchTransactionService;
import org.folio.service.transactions.batch.BatchTransactionServiceInterface;
import org.folio.service.transactions.batch.BatchTransferService;
import org.folio.service.transactions.cancel.CancelPaymentCreditService;
import org.folio.service.transactions.cancel.CancelPendingPaymentService;
import org.folio.service.transactions.cancel.CancelTransactionService;
import org.folio.service.transactions.restriction.EncumbranceRestrictionService;
import org.folio.service.transactions.restriction.PaymentCreditRestrictionService;
import org.folio.service.transactions.restriction.PendingPaymentRestrictionService;
import org.folio.service.transactions.restriction.TransactionRestrictionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

public class ServicesConfiguration {

  @Bean
  public BudgetService budgetService(BudgetDAO budgetDAO) {
    return new BudgetService(budgetDAO);
  }

  @Bean
  public FiscalYearService fiscalYearService(FiscalYearDAO fiscalYearDAO) {
    return new FiscalYearService(fiscalYearDAO);
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
  public TransactionSummaryService encumbranceSummaryService(@Qualifier("orderTransactionSummaryDao") TransactionSummaryDao orderTransactionSummaryDao) {
    return new EncumbranceTransactionSummaryService(orderTransactionSummaryDao);
  }

  @Bean
  public TransactionSummaryService paymentCreditSummaryService(@Qualifier("invoiceTransactionSummaryDao") TransactionSummaryDao invoiceTransactionSummaryDao) {
    return new PaymentCreditTransactionSummaryService(invoiceTransactionSummaryDao);
  }

  @Bean
  public TransactionSummaryService pendingPaymentSummaryService(@Qualifier("invoiceTransactionSummaryDao") TransactionSummaryDao invoiceTransactionSummaryDao) {
    return new PendingPaymentTransactionSummaryService(invoiceTransactionSummaryDao);
  }

  @Bean
  public TransactionRestrictionService encumbranceRestrictionService(BudgetService budgetService, LedgerService ledgerService) {
    return new EncumbranceRestrictionService(budgetService, ledgerService);
  }

  @Bean
  public TransactionRestrictionService pendingPaymentRestrictionService(BudgetService budgetService, LedgerService ledgerService,
                                                                        @Qualifier("pendingPaymentDAO") TransactionDAO pendingPaymentDAO) {
    return new PendingPaymentRestrictionService(budgetService, ledgerService, pendingPaymentDAO);
  }

  @Bean
  public TransactionRestrictionService paymentCreditRestrictionService(BudgetService budgetService, LedgerService ledgerService,
                                                                       @Qualifier("paymentCreditDAO") TransactionDAO paymentCreditDAO) {
    return new PaymentCreditRestrictionService(budgetService, ledgerService, paymentCreditDAO);
  }

  @Bean
  public DBClientFactory dbClientFactory() {
    return new DBClientFactory();
  }

  @Bean
  public AllOrNothingTransactionService allOrNothingEncumbranceService(@Qualifier("encumbranceDAO") TransactionDAO encumbranceDAO,
                                                                       TemporaryOrderTransactionDAO orderTransactionSummaryDao,
                                                                       EncumbranceTransactionSummaryService encumbranceSummaryService,
                                                                       EncumbranceRestrictionService encumbranceRestrictionService) {
    return new AllOrNothingTransactionService(encumbranceDAO, orderTransactionSummaryDao, encumbranceSummaryService,
                                            encumbranceRestrictionService);
  }

  @Bean
  public AllOrNothingTransactionService allOrNothingPaymentCreditService(@Qualifier("paymentCreditDAO") TransactionDAO paymentCreditDAO,
                                                                         TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO,
                                                                         PaymentCreditTransactionSummaryService paymentCreditSummaryService,
                                                                         PaymentCreditRestrictionService paymentCreditRestrictionService) {
    return new AllOrNothingTransactionService(paymentCreditDAO, temporaryInvoiceTransactionDAO, paymentCreditSummaryService,
                                              paymentCreditRestrictionService);
  }

  @Bean
  public AllOrNothingTransactionService allOrNothingPendingPaymentService(@Qualifier("pendingPaymentDAO") TransactionDAO pendingPaymentDAO,
                                                                          TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO,
                                                                          PendingPaymentTransactionSummaryService pendingPaymentSummaryService,
                                                                          PendingPaymentRestrictionService pendingPaymentRestrictionService) {
    return new AllOrNothingTransactionService(pendingPaymentDAO, temporaryInvoiceTransactionDAO, pendingPaymentSummaryService,
                                              pendingPaymentRestrictionService);
  }

  @Bean
  public TransactionService pendingPaymentService(@Qualifier("allOrNothingPendingPaymentService") AllOrNothingTransactionService allOrNothingPendingPaymentService,
                                                  @Qualifier("pendingPaymentDAO") TransactionDAO pendingPaymentDAO,
                                                  BudgetService budgetService,
                                                  @Qualifier("cancelPendingPaymentService") CancelTransactionService cancelPendingPaymentService) {

    return new PendingPaymentService(allOrNothingPendingPaymentService, pendingPaymentDAO, budgetService, cancelPendingPaymentService);
  }

  @Bean
  public CancelTransactionService cancelPendingPaymentService(BudgetService budgetService,
                                                              @Qualifier("paymentCreditDAO") TransactionDAO paymentCreditDAO,
                                                              @Qualifier("encumbranceDAO") TransactionDAO encumbranceDAO) {

    return new CancelPendingPaymentService(budgetService, paymentCreditDAO, encumbranceDAO);
  }

  @Bean
  public TransactionService paymentCreditService(@Qualifier("allOrNothingPaymentCreditService") AllOrNothingTransactionService allOrNothingPaymentCreditService,
                                                 BudgetService budgetService,
                                                 @Qualifier("paymentCreditDAO") TransactionDAO paymentCreditDAO,
                                                 @Qualifier("cancelPaymentCreditService") CancelTransactionService cancelPaymentCreditService) {

    return new PaymentCreditService(allOrNothingPaymentCreditService, paymentCreditDAO, budgetService, cancelPaymentCreditService);
  }

  @Bean
  public CancelTransactionService cancelPaymentCreditService(BudgetService budgetService,
                                                             @Qualifier("paymentCreditDAO") TransactionDAO paymentCreditDAO,
                                                             @Qualifier("encumbranceDAO") TransactionDAO encumbranceDAO) {

    return new CancelPaymentCreditService(budgetService, paymentCreditDAO, encumbranceDAO);
  }

  @Bean
  public TransactionService encumbranceService(@Qualifier("allOrNothingEncumbranceService") AllOrNothingTransactionService allOrNothingEncumbranceService,
                                               @Qualifier("encumbranceDAO") TransactionDAO encumbranceDAO,
                                               BudgetService budgetService) {

    return new EncumbranceService(allOrNothingEncumbranceService, encumbranceDAO, budgetService);
  }

  @Bean
  public TransactionService allocationService(BudgetService budgetService,
                                              @Qualifier("defaultTransactionDAO") TransactionDAO defaultTransactionDAO) {
    return new AllocationService(budgetService, defaultTransactionDAO);
  }

  @Bean
  public TransactionService transferService(BudgetService budgetService,
                                            @Qualifier("defaultTransactionDAO") TransactionDAO defaultTransactionDAO) {
    return new TransferService(budgetService, defaultTransactionDAO);
  }

  @Bean
  public TransactionManagingStrategyFactory transactionManagingStrategyFactory(Set<TransactionManagingStrategy> transactionServices) {
    return new TransactionManagingStrategyFactory(transactionServices);
  }

  @Bean
  public DefaultTransactionService defaultTransactionService(@Qualifier("defaultTransactionDAO") TransactionDAO defaultTransactionDAO) {
    return new DefaultTransactionService(defaultTransactionDAO);
  }

  @Bean
  public BatchAllocationService batchAllocationService() {
    return new BatchAllocationService();
  }

  @Bean
  public BatchEncumbranceService batchEncumbranceService() {
    return new BatchEncumbranceService();
  }

  @Bean
  public BatchPaymentCreditService batchPaymentCreditService() {
    return new BatchPaymentCreditService();
  }

  @Bean
  public BatchPendingPaymentService batchPendingPaymentService() {
    return new BatchPendingPaymentService();
  }

  @Bean
  public BatchTransferService batchTransferService() {
    return new BatchTransferService();
  }

  @Bean
  public BatchTransactionService batchTransactionService(DBClientFactory dbClientFactory, BatchTransactionDAO batchTransactionDAO,
      FundService fundService, BudgetService budgetService, LedgerService ledgerService,
      Set<BatchTransactionServiceInterface> batchTransactionStrategies) {
    return new BatchTransactionService(dbClientFactory, batchTransactionDAO, fundService, budgetService, ledgerService,
      batchTransactionStrategies);
  }

  @Bean
  public PostgresFunctionExecutionService postgresFunctionExecutionService() {
    return new PostgresFunctionExecutionService();
  }

  @Bean
  public RolloverProgressService rolloverProgressService(RolloverProgressDAO rolloverProgressDAO, RolloverErrorService rolloverErrorService) {
    return new RolloverProgressService(rolloverProgressDAO, rolloverErrorService);
  }

  @Bean
  public LedgerRolloverService ledgerRolloverService(FiscalYearService fiscalYearService,
    LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO,
    BudgetService budgetService,
    RolloverProgressService rolloverProgressService,
    RolloverErrorService rolloverErrorService,
    RolloverBudgetService rolloverBudgetService,
    PostgresFunctionExecutionService postgresFunctionExecutionService,
    RolloverValidationService rolloverValidationService,
    @Qualifier("orderRolloverRestClient") RestClient orderRolloverRestClient,
    EmailService emailService) {
    return new LedgerRolloverService(fiscalYearService, ledgerFiscalYearRolloverDAO, budgetService, rolloverProgressService, rolloverErrorService,
      rolloverBudgetService, postgresFunctionExecutionService, rolloverValidationService, orderRolloverRestClient, emailService);
  }

  @Bean
  public RolloverErrorService rolloverErrorService(RolloverErrorDAO rolloverErrorDAO) {
    return new RolloverErrorService(rolloverErrorDAO);
  }

  @Bean
  public EmailService emailService(@Qualifier("configurationRestClient") RestClient configurationRestClient,
                                   @Qualifier("userRestClient") RestClient userRestClient,
                                   LedgerDAO ledgerDAO) {
    return new EmailService(configurationRestClient, userRestClient, ledgerDAO);
  }

  @Bean
  public RolloverBudgetService rolloverBudgetService(RolloverBudgetDAO rolloverBudgetDAO, RolloverBudgetExpenseClassTotalsService rolloverBudgetExpenseClassTotalsService) {
    return new RolloverBudgetService(rolloverBudgetDAO, rolloverBudgetExpenseClassTotalsService);
  }

  @Bean
  public RolloverValidationService rolloverValidationService() {
    return new RolloverValidationService();
  }

  @Bean
  BudgetExpenseClassService budgetExpenseClassService(BudgetExpenseClassDAO budgetExpenseClassDAO, ExpenseClassDAO expenseClassDAO) {
    return new BudgetExpenseClassService(expenseClassDAO, budgetExpenseClassDAO);
  }

  @Bean
  TemporaryTransactionService temporaryTransactionService(TemporaryEncumbranceTransactionDAO temporaryEncumbranceTransactionDAO) {
    return new TemporaryTransactionService(temporaryEncumbranceTransactionDAO);
  }

  @Bean
  RolloverBudgetExpenseClassTotalsService rolloverBudgetExpenseClassTotalsService(BudgetExpenseClassService budgetExpenseClassService, TemporaryTransactionService temporaryTransactionService) {
    return new RolloverBudgetExpenseClassTotalsService(budgetExpenseClassService, temporaryTransactionService);
  }
}
