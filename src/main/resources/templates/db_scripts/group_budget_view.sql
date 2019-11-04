CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.group_budgets_view
AS SELECT ${myuniversity}_${mymodule}.budget.id AS id, ${myuniversity}_${mymodule}.budget.jsonb AS jsonb, ${myuniversity}_${mymodule}.groups.jsonb AS group_jsonb
FROM ${myuniversity}_${mymodule}.budget LEFT JOIN ${myuniversity}_${mymodule}.group_fund_fiscal_year ON ${myuniversity}_${mymodule}.budget.id = ${myuniversity}_${mymodule}.group_fund_fiscal_year.budgetId
LEFT JOIN ${myuniversity}_${mymodule}.groups ON ${myuniversity}_${mymodule}.group_fund_fiscal_year.groupId = ${myuniversity}_${mymodule}.groups.id;
