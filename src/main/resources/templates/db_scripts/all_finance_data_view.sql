CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.finance_data_view AS
SELECT
  DISTINCT ON (fiscal_year.id, fund.id, budget.id)
  fiscal_year.id as id,
  jsonb_build_object(
    'fiscalYearId', fiscal_year.id,
    'fiscalYearCode', fiscal_year.jsonb ->>'code',
    'fundId', fund.id,
    'fundCode', fund.jsonb ->>'code',
    'fundName', fund.jsonb ->>'name',
    'fundDescription', fund.jsonb ->>'description',
    'fundStatus', fund.jsonb ->>'fundStatus',
    'fundTags', jsonb_build_object('tagList', fund.jsonb -> 'tags' -> 'tagList'),
    'fundAcqUnitIds', fund.jsonb ->'acqUnitIds',
    'ledgerId', ledger.id,
    'ledgerCode', ledger.jsonb ->> 'code',
    'budgetId', budget.id,
    'budgetName', budget.jsonb ->>'name',
    'budgetStatus', budget.jsonb ->>'budgetStatus',
    'budgetInitialAllocation', budget.jsonb ->>'initialAllocation',
    'budgetCurrentAllocation', (budget.jsonb->'initialAllocation')::decimal + (budget.jsonb->'allocationTo')::decimal - (budget.jsonb->'allocationFrom')::decimal,
    'budgetAllowableExpenditure', budget.jsonb ->>'allowableExpenditure',
    'budgetAllowableEncumbrance', budget.jsonb ->>'allowableEncumbrance',
    'budgetAcqUnitIds', budget.jsonb ->'acqUnitIds',
    'groupId', groups.id,
    'groupCode', groups.jsonb ->> 'code'
  ) as jsonb
FROM ${myuniversity}_${mymodule}.fiscal_year
INNER JOIN ${myuniversity}_${mymodule}.fiscal_year fy_one
    ON fy_one.jsonb->>'series' = fiscal_year.jsonb->>'series'
INNER JOIN ${myuniversity}_${mymodule}.ledger
    ON ledger.fiscalyearoneid = fy_one.id
INNER JOIN ${myuniversity}_${mymodule}.fund
    ON fund.ledgerid = ledger.id
LEFT OUTER JOIN ${myuniversity}_${mymodule}.budget
    ON budget.fundid = fund.id AND budget.fiscalYearId = fiscal_year.id
LEFT OUTER JOIN ${myuniversity}_${mymodule}.group_fund_fiscal_year
    ON group_fund_fiscal_year.fundid = fund.id AND group_fund_fiscal_year.fiscalYearId = fiscal_year.id AND group_fund_fiscal_year.budgetId = budget.id
LEFT OUTER JOIN ${myuniversity}_${mymodule}.groups
    ON groups.id = group_fund_fiscal_year.groupid
ORDER BY fiscal_year.id, fund.id, budget.id;
