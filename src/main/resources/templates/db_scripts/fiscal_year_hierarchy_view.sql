CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.fiscal_year_hierarchy_view AS
SELECT
  ledger.id as id,
  fiscal_year.id as fiscalyearid,
  jsonb_build_object(
    'fiscalYearId', fiscal_year.id,
    'fiscalYearCode', fiscal_year.jsonb ->> 'code',
    'ledgerId', ledger.id,
    'ledgerCode', ledger.jsonb ->> 'code',
    'ledgerName', ledger.jsonb ->> 'name',
    'ledgerStatus', ledger.jsonb ->> 'ledgerStatus',
    'groupId', groups.id,
    'groupCode', groups.jsonb ->> 'code',
    'groupName', groups.jsonb ->> 'name',
    'groupStatus', groups.jsonb ->> 'status',
    'fundId', fund.id,
    'fundCode', fund.jsonb ->> 'code',
    'fundName', fund.jsonb ->> 'name',
    'fundStatus', fund.jsonb ->> 'fundStatus',
    'budgetId', budget.id,
    'budgetName', budget.jsonb ->> 'name',
    'budgetStatus', budget.jsonb ->> 'budgetStatus',
    'initialAllocation', (budget.jsonb ->> 'initialAllocation')::numeric,
    'allocated', (budget.jsonb ->> 'allocated')::numeric,
    'available', (budget.jsonb ->> 'available')::numeric,
    'budgetExpenseClasses', (
      SELECT jsonb_agg(
        jsonb_build_object(
          'expenseClassId', expense_class.id,
          'expenseClassCode', expense_class.jsonb ->> 'code',
          'expenseClassName', expense_class.jsonb ->> 'name',
          'status', budget_expense_class.jsonb ->> 'status'
        )
      )
      FROM ${myuniversity}_${mymodule}.budget_expense_class
      INNER JOIN ${myuniversity}_${mymodule}.expense_class
        ON expense_class.id = budget_expense_class.expenseclassid
      WHERE budget_expense_class.budgetid = budget.id
    )
  ) as jsonb
FROM ${myuniversity}_${mymodule}.fiscal_year
INNER JOIN ${myuniversity}_${mymodule}.budget
  ON budget.fiscalyearid = fiscal_year.id
INNER JOIN ${myuniversity}_${mymodule}.fund
  ON fund.id = budget.fundid
INNER JOIN ${myuniversity}_${mymodule}.ledger
  ON ledger.id = fund.ledgerid
LEFT OUTER JOIN ${myuniversity}_${mymodule}.group_fund_fiscal_year
  ON group_fund_fiscal_year.fundid = fund.id AND group_fund_fiscal_year.fiscalyearid = fiscal_year.id
LEFT OUTER JOIN ${myuniversity}_${mymodule}.groups
  ON groups.id = group_fund_fiscal_year.groupid;
