-- Flat view: one row per (fiscal year, ledger, group, fund, budget, expense class).
-- Expense class and group may be null (budgets with no expense classes; funds not in a group).
-- Java layer aggregates these rows into the Ledger -> Group -> Fund -> Budget -> Expense Class tree.
CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.fiscal_year_hierarchy_view AS
SELECT
  row_number() OVER () AS id,
  jsonb_build_object(
    'fiscalYearId', fy.id,
    'ledgerId', ledger.id,
    'ledgerName', ledger.jsonb ->> 'name',
    'ledgerCode', ledger.jsonb ->> 'code',
    'groupId', groups.id,
    'groupName', groups.jsonb ->> 'name',
    'groupCode', groups.jsonb ->> 'code',
    'fundId', fund.id,
    'fundName', fund.jsonb ->> 'name',
    'fundCode', fund.jsonb ->> 'code',
    'budgetId', budget.id,
    'budgetName', budget.jsonb ->> 'name',
    'expenseClassId', expense_class.id,
    'expenseClassName', expense_class.jsonb ->> 'name',
    'expenseClassCode', expense_class.jsonb ->> 'code'
  ) AS jsonb
FROM ${myuniversity}_${mymodule}.fiscal_year fy
INNER JOIN ${myuniversity}_${mymodule}.budget ON budget.fiscalyearid = fy.id
INNER JOIN ${myuniversity}_${mymodule}.fund ON fund.id = budget.fundid
INNER JOIN ${myuniversity}_${mymodule}.ledger ON ledger.id = fund.ledgerid
LEFT JOIN ${myuniversity}_${mymodule}.group_fund_fiscal_year gffy
  ON gffy.budgetid = budget.id AND gffy.fundid = fund.id AND gffy.fiscalyearid = fy.id
LEFT JOIN ${myuniversity}_${mymodule}.groups ON groups.id = gffy.groupid
LEFT JOIN ${myuniversity}_${mymodule}.budget_expense_class bec ON bec.budgetid = budget.id
LEFT JOIN ${myuniversity}_${mymodule}.expense_class ON expense_class.id = bec.expenseclassid;
