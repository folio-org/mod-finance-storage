-- Extract credits from expenditures in each budget
-- 1. Calculate all credits values within budget fiscal year
-- 2. Set to budget credits fields
-- 3. Subtract from expenditures amount

WITH credit_sums AS (
    SELECT
        budget.id AS budget_id,
        COALESCE(SUM((trx.jsonb->>'amount')::numeric), 0) AS total_credits
    FROM ${myuniversity}_${mymodule}.budget AS budget
    LEFT JOIN ${myuniversity}_${mymodule}.transaction AS trx
        ON trx.fiscalyearid = budget.fiscalyearid AND trx.tofundid = budget.fundid
        AND trx.jsonb->>'transactionType' = 'Credit'
    GROUP BY budget.id
)
UPDATE ${myuniversity}_${mymodule}.budget AS budget
SET
    jsonb = jsonb || jsonb_build_object(
        'credits', to_jsonb(COALESCE((budget.jsonb->>'credits')::numeric, 0) + credit_sums.total_credits),
        'expenditures', to_jsonb(COALESCE((budget.jsonb->>'expenditures')::numeric, 0) - credit_sums.total_credits)
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
    jsonb = jsonb || jsonb_build_object(
        'encumbrance', jsonb_build_object(
            'amountExpended', to_jsonb(aggregated_amounts.total_expended),
            'amountCredited', to_jsonb(aggregated_amounts.total_credited)
        )
    )
FROM aggregated_amounts
WHERE encumbrance.id = aggregated_amounts.encumbrance_id;
