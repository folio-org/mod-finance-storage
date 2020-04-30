UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "User"}'
WHERE
  (jsonb ->> 'source') = 'Manual';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "PoLine"}'
WHERE
  jsonb ? 'encumbrance.sourcePurchaseOrderId' AND jsonb ? 'encumbrance.sourcePoLineId' AND jsonb ->> 'transactionType' = 'Encumbrance';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "Invoice"}'
WHERE
  (jsonb ->> 'source') = 'Credit';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "Invoice"}'
WHERE
  (jsonb ->> 'source') = 'Voucher';
