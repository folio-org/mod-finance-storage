package org.folio.config;

import java.util.Set;

import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.budget.BudgetExpenseClassDAO;
import org.folio.dao.exchangerate.ExchangeRateSourceDAO;
import org.folio.dao.expense.ExpenseClassDAO;
import org.folio.dao.fiscalyear.FiscalYearDAO;
import org.folio.dao.fund.FundDAO;
import org.folio.dao.group.GroupDAO;
import org.folio.dao.jobnumber.JobNumberDAO;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.dao.rollover.RolloverBudgetDAO;
import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.dao.transactions.TemporaryEncumbranceDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetExpenseClassService;
import org.folio.service.budget.BudgetService;
import org.folio.service.budget.RolloverBudgetExpenseClassTotalsService;
import org.folio.service.email.EmailService;
import org.folio.service.exchangerate.ExchangeRateSourceService;
import org.folio.service.financedata.FinanceDataService;
import org.folio.service.fiscalyear.FiscalYearService;
import org.folio.service.fund.FundService;
import org.folio.service.fund.StorageFundService;
import org.folio.service.group.GroupService;
import org.folio.service.jobnumber.JobNumberService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledger.StorageLedgerService;
import org.folio.service.rollover.LedgerRolloverService;
import org.folio.service.rollover.RolloverBudgetService;
import org.folio.service.rollover.RolloverErrorService;
import org.folio.service.rollover.RolloverProgressService;
import org.folio.service.rollover.RolloverValidationService;
import org.folio.service.transactions.TemporaryEncumbranceService;
import org.folio.service.transactions.batch.BatchAllocationService;
import org.folio.service.transactions.batch.BatchEncumbranceService;
import org.folio.service.transactions.batch.BatchPaymentCreditService;
import org.folio.service.transactions.batch.BatchPendingPaymentService;
import org.folio.service.transactions.batch.BatchTransactionService;
import org.folio.service.transactions.batch.BatchTransactionServiceInterface;
import org.folio.service.transactions.batch.BatchTransferService;
import org.folio.tools.store.SecureStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

public class ServicesConfiguration {

  @Bean
  public BudgetService budgetService(DBClientFactory dbClientFactory, BudgetDAO budgetDAO, GroupService groupService) {
    return new BudgetService(dbClientFactory, budgetDAO, groupService);
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
  public GroupService groupService(DBClientFactory dbClientFactory, GroupDAO groupDAO) {
    return new GroupService(dbClientFactory, groupDAO);
  }

  @Bean
  public LedgerService ledgerService(LedgerDAO ledgerDAO, FundService fundService) {
    return new StorageLedgerService(ledgerDAO, fundService);
  }

  @Bean
  public DBClientFactory dbClientFactory() {
    return new DBClientFactory();
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
  TemporaryEncumbranceService temporaryEncumbranceService(TemporaryEncumbranceDAO temporaryEncumbranceDAO) {
    return new TemporaryEncumbranceService(temporaryEncumbranceDAO);
  }

  @Bean
  RolloverBudgetExpenseClassTotalsService rolloverBudgetExpenseClassTotalsService(BudgetExpenseClassService budgetExpenseClassService,
      TemporaryEncumbranceService temporaryEncumbranceService) {
    return new RolloverBudgetExpenseClassTotalsService(budgetExpenseClassService, temporaryEncumbranceService);
  }

  @Bean
  public FinanceDataService financeDataService(FundService fundService, BudgetService budgetService, FiscalYearService fiscalYearService,
      BatchTransactionService batchTransactionService) {
    return new FinanceDataService(fundService, budgetService, fiscalYearService, batchTransactionService);
  }

  @Bean
  public JobNumberService jobNumberService(JobNumberDAO jobNumberDAO) {
    return new JobNumberService(jobNumberDAO);
  }

  @Bean
  public ExchangeRateSourceService exchangeRateSourceService(SecureStore secureStore, ExchangeRateSourceDAO exchangeRateSourceDAO) {
    return new ExchangeRateSourceService(secureStore, exchangeRateSourceDAO);
  }
}
