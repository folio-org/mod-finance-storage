package org.folio.service.budget;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.jaxrs.model.BudgetExpenseClassTotal;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.DBConn;
import org.folio.service.transactions.TemporaryTransactionService;
import org.folio.utils.MoneyUtils;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.groupingBy;

public class RolloverBudgetExpenseClassTotalsService {

  private final BudgetExpenseClassService budgetExpenseClassService;
  private final TemporaryTransactionService temporaryTransactionService;

  public RolloverBudgetExpenseClassTotalsService(BudgetExpenseClassService budgetExpenseClassService, TemporaryTransactionService temporaryTransactionService) {
    this.budgetExpenseClassService = budgetExpenseClassService;
    this.temporaryTransactionService = temporaryTransactionService;
  }

  public Future<LedgerFiscalYearRolloverBudget> getBudgetWithUpdatedExpenseClassTotals(LedgerFiscalYearRolloverBudget budget,
      DBConn conn) {
    return budgetExpenseClassService.getExpenseClassesByTemporaryBudgetId(budget.getBudgetId(), conn)
      .compose(expenseClasses -> temporaryTransactionService.getTransactions(budget, conn)
        .map(transactions -> buildBudgetExpenseClassesTotals(expenseClasses, transactions, budget)))
      .compose(budgetExpenseClassTotals -> budgetExpenseClassService.getTempBudgetExpenseClasses(budget.getBudgetId(), conn)
        .map(budgetExpenseClasses -> updateExpenseClassStatus(budgetExpenseClassTotals, budgetExpenseClasses)))
      .map(budget::withExpenseClassDetails);
  }

  private List<BudgetExpenseClassTotal> buildBudgetExpenseClassesTotals(List<ExpenseClass> expenseClasses, List<Transaction> transactions, LedgerFiscalYearRolloverBudget budget) {

    double totalExpended = getBudgetTotalExpended(budget);

    Map<String, List<Transaction>> groupedByExpenseClassId = transactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getExpenseClassId()))
      .collect(groupingBy(Transaction::getExpenseClassId));

    Map<ExpenseClass, List<Transaction>> groupedByExpenseClass = expenseClasses.stream()
      .collect(toMap(Function.identity(), expenseClass -> groupedByExpenseClassId.getOrDefault(expenseClass.getId(), Collections.emptyList())));

    return buildBudgetExpenseClassesTotals(groupedByExpenseClass, totalExpended);
  }

  private double getBudgetTotalExpended(LedgerFiscalYearRolloverBudget budget) {
    BigDecimal totalExpended = BigDecimal.valueOf(budget.getExpenditures()).add(BigDecimal.valueOf(budget.getOverExpended()));
    return totalExpended.doubleValue();
  }

  private List<BudgetExpenseClassTotal> buildBudgetExpenseClassesTotals(Map<ExpenseClass, List<Transaction>> groupedByExpenseClass, double totalExpended) {
    return groupedByExpenseClass.entrySet().stream()
      .map(entry -> buildBudgetExpenseClassTotals(entry.getKey(), entry.getValue(), totalExpended))
      .collect(Collectors.toList());
  }

  private BudgetExpenseClassTotal buildBudgetExpenseClassTotals(ExpenseClass expenseClass, List<Transaction> transactions, double totalExpended) {
    double encumbered = 0d;
    double awaitingPayment = 0d;
    double expended = 0d;
    Double expendedPercentage = 0d;

    if (!transactions.isEmpty()) {
      CurrencyUnit currency = Monetary.getCurrency(transactions.get(0).getCurrency());
      Map<Transaction.TransactionType, List<Transaction>> transactionGroupedByType = transactions.stream().collect(groupingBy(Transaction::getTransactionType));

      encumbered = MoneyUtils.calculateTotalAmountWithRounding(
        transactionGroupedByType.getOrDefault(Transaction.TransactionType.ENCUMBRANCE, Collections.emptyList()), currency);
      awaitingPayment = MoneyUtils.calculateTotalAmountWithRounding(
        transactionGroupedByType.getOrDefault(Transaction.TransactionType.PENDING_PAYMENT, Collections.emptyList()), currency);

      MonetaryAmount tmpExpended = MoneyUtils.calculateTotalAmount(
        transactionGroupedByType.getOrDefault(Transaction.TransactionType.PAYMENT, Collections.emptyList()), currency);
      tmpExpended = tmpExpended.subtract(MoneyUtils.calculateTotalAmount(
        transactionGroupedByType.getOrDefault(Transaction.TransactionType.CREDIT, Collections.emptyList()), currency));

      expended = tmpExpended.with(Monetary.getDefaultRounding()).getNumber().doubleValue();

      expendedPercentage = totalExpended == 0 ? null : MoneyUtils.calculateExpendedPercentage(tmpExpended, totalExpended);
    }

    return new BudgetExpenseClassTotal()
      .withId(expenseClass.getId())
      .withExpenseClassName(expenseClass.getName())
      .withExpenseClassCode(expenseClass.getCode())
      .withEncumbered(encumbered)
      .withAwaitingPayment(awaitingPayment)
      .withExpended(expended)
      .withPercentageExpended(expendedPercentage);
  }

  private List<BudgetExpenseClassTotal> updateExpenseClassStatus(List<BudgetExpenseClassTotal> budgetExpenseClassTotals,
                                                                 List<BudgetExpenseClass> budgetExpenseClasses) {
    Map<String, BudgetExpenseClassTotal.ExpenseClassStatus> idStatusMap = budgetExpenseClasses.stream()
      .collect(toMap(BudgetExpenseClass::getExpenseClassId, budgetExpenseClass -> BudgetExpenseClassTotal.ExpenseClassStatus.fromValue(budgetExpenseClass.getStatus().value())));
    budgetExpenseClassTotals.forEach(budgetExpenseClassTotal -> budgetExpenseClassTotal.setExpenseClassStatus(idStatusMap.get(budgetExpenseClassTotal.getId())));
    return budgetExpenseClassTotals;
  }

}
