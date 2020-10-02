UPDATE ${myuniversity}_${mymodule}.order_transaction_summaries AS summary
	SET jsonb = jsonb_set(jsonb,  '{numTransactions}', to_jsonb(-(jsonb->>'numTransactions')::integer), false)
	WHERE (SELECT COUNT(*) FROM ${myuniversity}_${mymodule}.transaction AS transaction
		   WHERE summary.id::text = transaction.jsonb->'encumbrance'->>'sourcePurchaseOrderId') > 0
		   AND (jsonb->>'numTransactions')::integer > 0;