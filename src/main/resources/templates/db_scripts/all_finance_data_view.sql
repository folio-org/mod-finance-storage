CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.finance_data_view AS
SELECT
  fiscal_year.id as id,
  jsonb_build_object(
    'fiscalYearId', fiscal_year.id,
    'fiscalYearCode', fiscal_year.jsonb ->>'code',
    'fundId', fund.id,
    'fundCode', fund.jsonb ->>'code',
    'fundName', fund.jsonb ->>'name',
    'fundDescription', fund.jsonb ->>'description',
    'fundStatus', fund.jsonb ->>'fundStatus',
    'fundTags', fund.jsonb ->'tags' -> 'tagList',
    'budgetId', budget.id,
    'budgetName', budget.jsonb ->>'name',
    'budgetStatus', budget.jsonb ->>'budgetStatus',
    'budgetInitialAllocation', budget.jsonb ->>'initialAllocation',
    'budgetCurrentAllocation', budget.jsonb ->>'allocated',
    'budgetAllowableExpenditure', budget.jsonb ->>'allowableExpenditure',
    'budgetAllowableEncumbrance', budget.jsonb ->>'allowableEncumbrance'
  ) as jsonb
FROM ${myuniversity}_${mymodule}.fiscal_year
LEFT OUTER JOIN ${myuniversity}_${mymodule}.ledger
  ON ledger.fiscalyearoneid = fiscal_year.id
LEFT OUTER JOIN ${myuniversity}_${mymodule}.fund
  ON fund.ledgerid = ledger.id
LEFT OUTER JOIN ${myuniversity}_${mymodule}.budget
  ON fund.id = budget.fundid;
