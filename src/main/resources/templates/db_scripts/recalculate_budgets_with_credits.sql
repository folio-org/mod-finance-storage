-- Recalculate expenditures from transactions for all budgets with credits, in all fiscal years (MODFIN-431)

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb = budget.jsonb || jsonb_build_object(
  'expenditures', to_jsonb(
    COALESCE(
	    (SELECT SUM(amounts.amount)
	      FROM (SELECT (jsonb->>'amount')::numeric AS amount
	        FROM ${myuniversity}_${mymodule}.transaction
	        WHERE jsonb->>'transactionType' = 'Payment'
	          AND fiscalYearId = budget.fiscalYearId
	          AND fromFundId = budget.fundId
	      ) AS amounts),
	    '0')
	)
)
WHERE (budget.jsonb->>'credits')::numeric > 0;
