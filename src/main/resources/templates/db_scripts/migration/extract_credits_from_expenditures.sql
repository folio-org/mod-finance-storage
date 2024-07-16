<#if mode.name() == "UPDATE">

-- Extract credits from expenditures in each budget
-- 1. Calculate all credits values within budget fiscal year
-- 2. Set to budget credits fields
-- 3. Subtract from expenditures amount

WITH credit_sums AS (
    SELECT
        budget.id AS budget_id,
        SUM((trx.jsonb->>'amount')::numeric) AS total_credits
    FROM ${myuniversity}_${mymodule}.budget AS budget
    JOIN ${myuniversity}_${mymodule}.transaction AS trx
        ON trx.jsonb->>'fiscalYearId' = budget.jsonb->>'fiscalYearId' AND
		trx.jsonb->>'fromFundId' = budget.jsonb->>'fundId'
    WHERE trx.jsonb->>'transactionType' = 'Credit'
    GROUP BY budget.id
)
UPDATE ${myuniversity}_${mymodule}.budget AS budget
SET
    jsonb = jsonb_set(
        jsonb_set(
            budget.jsonb,
            '{credits}',
            to_jsonb((budget.jsonb->>'credits')::numeric + credit_sums.total_credits)
        ),
        '{expenditures}',
        to_jsonb((budget.jsonb->>'expenditures')::numeric - credit_sums.total_credits)
    )
FROM credit_sums
WHERE budget.id = credit_sums.budget_id;


-- Recalculate amountExpended/amountCredited values in encumbrance transaction
-- 1. Calculate each amount with Payment and Credit transactions
-- 2. Set calculated values for amountExpended and amountCredits, respectively

WITH aggregated_amounts AS (
    SELECT
        encumbrance.id AS encumbrance_id,
        SUM(CASE WHEN trx.jsonb->>'transactionType' = 'Payment' THEN (trx.jsonb->>'amount')::numeric ELSE 0 END) AS total_expended,
        SUM(CASE WHEN trx.jsonb->>'transactionType' = 'Credit' THEN (trx.jsonb->>'amount')::numeric ELSE 0 END) AS total_credited
    FROM ${myuniversity}_${mymodule}.transaction AS encumbrance
    LEFT JOIN ${myuniversity}_${mymodule}.transaction AS trx
        ON trx.jsonb->>'paymentEncumbranceId' = encumbrance.id::text
    WHERE encumbrance.jsonb->>'transactionType' = 'Encumbrance'
    GROUP BY encumbrance.id
)
UPDATE ${myuniversity}_${mymodule}.transaction AS encumbrance
SET
    jsonb = jsonb_set(
        jsonb_set(
            encumbrance.jsonb,
            '{encumbrance,amountExpended}',
            to_jsonb(aggregated_amounts.total_expended)
        ),
        '{encumbrance,amountCredited}',
        to_jsonb(aggregated_amounts.total_credited)
    )
FROM aggregated_amounts
WHERE encumbrance.id = aggregated_amounts.encumbrance_id;
</#if>


INSERT INTO diku_mod_finance_storage.transaction (id, creation_date, created_by, fiscalyearid, fromfundid, jsonb)
SELECT
  gen_random_uuid() AS id,
  NOW() AS creation_date, -- or use a specific timestamp if needed
  '07da18fd-2ba0-5025-9c42-8bb124ddea63' AS created_by,
  '7a4c4d30-3b63-4102-8e2d-3ee5792d7d02' AS fiscalyearid, -- Fixed fiscalYearId
  '69640328-788e-43fc-9c3c-af39e243f3b7' AS fromfundid, -- Fixed fromFundId
  jsonb_build_object(
    'id', gen_random_uuid(), -- This will be overridden by the actual ID of the row
    'amount', 1, -- or use a specific amount as needed
    'source', 'Invoice',
    '_version', 1,
    'currency', 'USD',
    'metadata', jsonb_build_object(
      'createdDate', NOW(),
      'updatedDate', NOW(),
      'createdByUserId', '07da18fd-2ba0-5025-9c42-8bb124ddea63',
      'updatedByUserId', '07da18fd-2ba0-5025-9c42-8bb124ddea63'
    ),
    'fromFundId', '69640328-788e-43fc-9c3c-af39e243f3b7',
    'fiscalYearId', '7a4c4d30-3b63-4102-8e2d-3ee5792d7d02',
    'sourceInvoiceId', i.id,
    'transactionType', 'Payment'

  )
FROM diku_mod_invoice_storage.invoices i
LIMIT 5;
