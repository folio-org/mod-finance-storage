package org.folio.config;

import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.budget.BudgetExpenseClassDAO;
import org.folio.dao.budget.BudgetExpenseClassDAOImpl;
import org.folio.dao.budget.BudgetPostgresDAO;
import org.folio.dao.expense.ExpenseClassDAO;
import org.folio.dao.expense.ExpenseClassDAOImpl;
import org.folio.dao.fiscalyear.FiscalYearDAO;
import org.folio.dao.fiscalyear.FiscalYearPostgresDAO;
import org.folio.dao.fund.FundDAO;
import org.folio.dao.fund.FundPostgresDAO;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.dao.ledger.LedgerPostgresDAO;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.dao.rollover.RolloverBudgetDAO;
import org.folio.dao.rollover.RolloverErrorDAO;
import org.folio.dao.rollover.RolloverProgressDAO;
import org.folio.dao.summary.InvoiceTransactionSummaryDAO;
import org.folio.dao.summary.OrderTransactionSummaryDAO;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.dao.transactions.BatchTransactionPostgresDAO;
import org.folio.dao.transactions.DefaultTransactionDAO;
import org.folio.dao.transactions.EncumbranceDAO;
import org.folio.dao.transactions.PaymentCreditDAO;
import org.folio.dao.transactions.PendingPaymentDAO;
import org.folio.dao.transactions.TemporaryEncumbranceTransactionDAO;
import org.folio.dao.transactions.TemporaryInvoiceTransactionDAO;
import org.folio.dao.transactions.TemporaryOrderTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.springframework.context.annotation.Bean;

public class DAOConfiguration {

  @Bean
  public BudgetDAO budgetDAO() {
    return new BudgetPostgresDAO();
  }

  @Bean
  public FiscalYearDAO fiscalYearDAODAO() {
    return new FiscalYearPostgresDAO();
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
  public TransactionSummaryDao invoiceTransactionSummaryDao() {
    return new InvoiceTransactionSummaryDAO();
  }

  @Bean
  public TransactionSummaryDao orderTransactionSummaryDao() {
    return new OrderTransactionSummaryDAO();
  }

  @Bean
  public TemporaryInvoiceTransactionDAO temporaryInvoiceTransactionDAO() {
    return new TemporaryInvoiceTransactionDAO();
  }

  @Bean
  public TemporaryOrderTransactionDAO temporaryOrderTransactionDAO() {
    return new TemporaryOrderTransactionDAO();
  }

  @Bean
  public TemporaryEncumbranceTransactionDAO temporaryEncumbranceTransactionDAO() {
    return new TemporaryEncumbranceTransactionDAO();
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
  public TransactionDAO defaultTransactionDAO() {
    return new DefaultTransactionDAO();
  }

  @Bean
  public BatchTransactionDAO batchTransactionDAO() {
    return new BatchTransactionPostgresDAO();
  }

  @Bean
  public LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO() {
    return new LedgerFiscalYearRolloverDAO();
  }

  @Bean
  public RolloverProgressDAO rolloverProgressDAO() {
    return new RolloverProgressDAO();
  }

  @Bean
  public RolloverErrorDAO rolloverErrorDAO() {
    return new RolloverErrorDAO();
  }

  @Bean
  public RolloverBudgetDAO rolloverBudgetDAO() {
    return new RolloverBudgetDAO();
  }

  @Bean
  public ExpenseClassDAO expenseClassDAO() {
    return new ExpenseClassDAOImpl();
  }

  @Bean
  public BudgetExpenseClassDAO budgetExpenseClassDAO() {
    return new BudgetExpenseClassDAOImpl();
  }
}
