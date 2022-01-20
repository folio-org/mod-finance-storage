package org.folio.config;

import java.util.Set;

import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.fiscalyear.FiscalYearDAO;
import org.folio.dao.fund.FundDAO;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.dao.transactions.TemporaryInvoiceTransactionDAO;
import org.folio.dao.transactions.TemporaryOrderTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.RestClient;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetService;
import org.folio.service.fiscalyear.FiscalYearService;
import org.folio.service.fund.FundService;
import org.folio.service.fund.StorageFundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledger.StorageLedgerService;
import org.folio.service.rollover.LedgerRolloverService;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.service.rollover.RolloverProgressService;
import org.folio.service.summary.EncumbranceTransactionSummaryService;
import org.folio.service.summary.PaymentCreditTransactionSummaryService;
import org.folio.service.summary.PendingPaymentTransactionSummaryService;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.AllOrNothingTransactionService;
import org.folio.service.transactions.AllocationService;
import org.folio.service.transactions.EncumbranceService;
import org.folio.service.transactions.PaymentCreditService;
import org.folio.service.transactions.PendingPaymentService;
import org.folio.service.transactions.TransactionManagingStrategy;
import org.folio.service.transactions.TransactionManagingStrategyFactory;
import org.folio.service.transactions.TransactionService;
import org.folio.service.transactions.TransferService;
import org.folio.service.transactions.cancel.CancelPaymentCreditService;
import org.folio.service.transactions.cancel.CancelPendingPaymentService;
import org.folio.service.transactions.cancel.CancelTransactionService;
import org.folio.service.transactions.restriction.EncumbranceRestrictionService;
import org.folio.service.transactions.restriction.PaymentCreditRestrictionService;
import org.folio.service.transactions.restriction.PendingPaymentRestrictionService;
import org.folio.service.transactions.restriction.TransactionRestrictionService;
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
  public TransactionSummaryService encumbranceSummaryService(TransactionSummaryDao orderTransactionSummaryDao) {
    return new EncumbranceTransactionSummaryService(orderTransactionSummaryDao);
  }

  @Bean
  public TransactionSummaryService paymentCreditSummaryService(TransactionSummaryDao invoiceTransactionSummaryDao) {
    return new PaymentCreditTransactionSummaryService(invoiceTransactionSummaryDao);
  }

  @Bean
  public TransactionSummaryService pendingPaymentSummaryService(TransactionSummaryDao invoiceTransactionSummaryDao) {
    return new PendingPaymentTransactionSummaryService(invoiceTransactionSummaryDao);
  }

  @Bean
  public TransactionRestrictionService encumbranceRestrictionService(BudgetService budgetService, LedgerService ledgerService) {
    return new EncumbranceRestrictionService(budgetService, ledgerService);
  }

  @Bean
  public TransactionRestrictionService pendingPaymentRestrictionService(BudgetService budgetService, LedgerService ledgerService, TransactionDAO pendingPaymentDAO) {
    return new PendingPaymentRestrictionService(budgetService, ledgerService, pendingPaymentDAO);
  }

  @Bean
  public TransactionRestrictionService paymentCreditRestrictionService(BudgetService budgetService, LedgerService ledgerService, TransactionDAO paymentCreditDAO) {
    return new PaymentCreditRestrictionService(budgetService, ledgerService, paymentCreditDAO);
  }

  @Bean
  public AllOrNothingTransactionService allOrNothingEncumbranceService(TransactionDAO encumbranceDAO,
                                                                       TemporaryOrderTransactionDAO orderTransactionSummaryDao,
                                                                       EncumbranceTransactionSummaryService encumbranceSummaryService,
                                                                       EncumbranceRestrictionService encumbranceRestrictionService) {
    return new AllOrNothingTransactionService(encumbranceDAO, orderTransactionSummaryDao, encumbranceSummaryService, encumbranceRestrictionService);
  }

  @Bean
  public AllOrNothingTransactionService allOrNothingPaymentCreditService(TransactionDAO paymentCreditDAO,
                                                                         TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO,
                                                                         PaymentCreditTransactionSummaryService paymentCreditSummaryService,
                                                                         PaymentCreditRestrictionService paymentCreditRestrictionService) {
    return new AllOrNothingTransactionService(paymentCreditDAO, temporaryInvoiceTransactionDAO, paymentCreditSummaryService, paymentCreditRestrictionService);
  }

  @Bean
  public AllOrNothingTransactionService allOrNothingPendingPaymentService(TransactionDAO pendingPaymentDAO,
                                                                          TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO,
                                                                          PendingPaymentTransactionSummaryService pendingPaymentSummaryService,
                                                                          PendingPaymentRestrictionService pendingPaymentRestrictionService) {
    return new AllOrNothingTransactionService(pendingPaymentDAO, temporaryInvoiceTransactionDAO, pendingPaymentSummaryService, pendingPaymentRestrictionService);
  }

  @Bean
  public TransactionService pendingPaymentService(AllOrNothingTransactionService allOrNothingPendingPaymentService,
                                                  TransactionDAO pendingPaymentDAO,
                                                  BudgetService budgetService,
                                                  CancelTransactionService cancelPendingPaymentService) {

    return new PendingPaymentService(allOrNothingPendingPaymentService, pendingPaymentDAO, budgetService, cancelPendingPaymentService);
  }

  @Bean
  public CancelTransactionService cancelPendingPaymentService(TransactionDAO pendingPaymentDAO,
                                                           BudgetService budgetService) {

    return new CancelPendingPaymentService(pendingPaymentDAO, budgetService);
  }

  @Bean
  public TransactionService paymentCreditService(AllOrNothingTransactionService allOrNothingPaymentCreditService,
                                                 BudgetService budgetService,
                                                 TransactionDAO paymentCreditDAO,
                                                 CancelTransactionService cancelPaymentCreditService) {

    return new PaymentCreditService(allOrNothingPaymentCreditService, paymentCreditDAO, budgetService, cancelPaymentCreditService);
  }

  @Bean
  public CancelTransactionService cancelPaymentCreditService(TransactionDAO paymentCreditDAO,
                                                             BudgetService budgetService) {

    return new CancelPaymentCreditService(paymentCreditDAO, budgetService);
  }

  @Bean
  public TransactionService encumbranceService(AllOrNothingTransactionService allOrNothingEncumbranceService,
                                               TransactionDAO encumbranceDAO,
                                               BudgetService budgetService) {

    return new EncumbranceService(allOrNothingEncumbranceService, encumbranceDAO, budgetService);
  }

  @Bean
  public TransactionService allocationService(BudgetService budgetService) {
    return new AllocationService(budgetService);
  }

  @Bean
  public TransactionService transferService(BudgetService budgetService) {
    return new TransferService(budgetService);
  }

  @Bean
  public TransactionManagingStrategyFactory transactionManagingStrategyFactory(Set<TransactionManagingStrategy> transactionServices) {
    return new TransactionManagingStrategyFactory(transactionServices);
  }

  @Bean
  public PostgresFunctionExecutionService postgresFunctionExecutionService() {
    return new PostgresFunctionExecutionService();
  }

  @Bean
  public RolloverProgressService rolloverProgressService(RolloverProgressDAO rolloverProgressDAO, RolloverErrorDAO rolloverErrorDAO) {
    return new RolloverProgressService(rolloverProgressDAO, rolloverErrorDAO);
  }

  @Bean
  public LedgerRolloverService ledgerRolloverService(FiscalYearService fiscalYearService,
    LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO,
    BudgetService budgetService,
    RolloverProgressService rolloverProgressService,
    PostgresFunctionExecutionService postgresFunctionExecutionService,
    RestClient orderRolloverRestClient) {
    return new LedgerRolloverService(fiscalYearService, ledgerFiscalYearRolloverDAO, budgetService, rolloverProgressService,
      postgresFunctionExecutionService, orderRolloverRestClient);
  }

}
