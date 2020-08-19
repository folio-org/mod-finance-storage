 -- Update budgets
UPDATE ${myuniversity}_${mymodule}.budget
  SET
    jsonb = jsonb || jsonb_build_object('netTransfers', totalNetTransfers.totalNetTransfer)
  FROM
		 (SELECT  calc.budgetId AS budgetId, sum(calc.calculatedNetTransfer) AS totalNetTransfer FROM
				(SELECT budget.id as budgetId, budget.fundId as budgetFundId, transactions.fromFundId as transactionsFromFundId, transactions.toFundId as transactionsToFundId, transactions.fiscalYearId,
							(CASE WHEN transactions.fromFundId = budget.fundId THEN -sum((transactions.jsonb->>'amount')::decimal)
							 ELSE sum((transactions.jsonb->>'amount')::decimal) END) as calculatedNetTransfer
					FROM ${myuniversity}_${mymodule}.transaction AS transactions
						LEFT JOIN ${myuniversity}_${mymodule}.budget AS budget
										ON (transactions.fromFundId = budget.fundId AND transactions.fiscalYearId = budget.fiscalYearId)
												OR (transactions.toFundId = budget.fundId AND transactions.fiscalYearId = budget.fiscalYearId)
					WHERE transactions.jsonb->>'transactionType'='Transfer'
					GROUP BY budget.id, budget.fundId, transactions.fiscalYearId, transactions.fromFundId, transactions.toFundId) as calc
		  GROUP BY calc.budgetId) as totalNetTransfers
WHERE id = totalNetTransfers.budgetId;
