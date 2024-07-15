<#if mode.name() == "UPDATE">

-- Extract credits from expenditures in each budget
-- 1. Calculate all credits values within budget fiscal year
-- 2. Set to budget credits fields
-- 3. Subtract from expenditures amount

WITH credit_sums AS (
    SELECT
        budget.id AS budget_id,
        SUM(CASE WHEN trx.jsonb->>'transactionType' = 'Credit' THEN (trx.jsonb->>'amount')::numeric ELSE 0 END) AS total_credits
    FROM ${myuniversity}_${mymodule}.budget AS budget
    JOIN ${myuniversity}_${mymodule}.transaction AS trx
        ON trx.jsonb->>'fiscalYearId' = budget.jsonb->>'fiscalYearId'
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
