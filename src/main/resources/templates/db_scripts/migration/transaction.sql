-- Rename fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb_set(jsonb - '{awaitingPayment, encumbered, expenditures}'::text[], '{encumbrance}',
     jsonb_strip_nulls(jsonb_build_object('amountAwaitingPayment', jsonb::json -> 'awaitingPayment')
      || jsonb_build_object('initialAmountEncumbered', jsonb::json -> 'encumbered')
	    || jsonb_build_object('amountExpended', jsonb::json -> 'expenditures')))
WHERE
  NOT jsonb ? 'encumbrance';

-- Required fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb->'encumbrance' || jsonb_build_object('initialAmountEncumbered', 0)
WHERE
  NOT jsonb->'encumbrance' ? 'initialAmountEncumbered';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('currency', 'USD')
WHERE
  NOT jsonb ? 'currency';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('fiscalYearId',
  (SELECT ${myuniversity}_${mymodule}.budget.fiscalYearId
  	FROM ${myuniversity}_${mymodule}.budget
  	WHERE ${myuniversity}_${mymodule}.budget.id::text
    = (SELECT (jsonb::json ->> 'budgetId')::text FROM ${myuniversity}_${mymodule}.transaction LIMIT 1)))
WHERE NOT
  jsonb ?'fiscalYearId';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('source', 'Manual')
WHERE
  NOT jsonb ? 'source'
  OR (jsonb::json->>'source' <> 'Credit' AND jsonb::json ->> 'source' <> 'Manual' AND jsonb::json->>'source' <> 'User'
  AND jsonb::json ->> 'source' <> 'Voucher');

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('transactionType', 'Allocation')
WHERE
  NOT jsonb ? 'transactionType'
  OR (jsonb::json ->> 'transactionType' <> 'Allocation' AND jsonb::json ->> 'transactionType' <> 'Credit'
  AND jsonb::json ->> 'transactionType' <> 'Encumbrance'
  AND jsonb::json ->> 'transactionType' <> 'Payment' AND jsonb::json ->> 'transactionType' <> 'Transfer');

-- Update tags

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('tags', jsonb_build_object('tagList', jsonb::json -> 'tags'))
WHERE
  jsonb::json #> '{tags, tagList}' iS NULL AND jsonb ? 'tags';

-- Remove fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb - '{available, note, overcharge, timestamp, sourceId, budgetId}'::text[];
