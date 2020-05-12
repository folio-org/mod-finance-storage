UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "User"}'
WHERE
  jsonb ->> 'transactionType' IN ('Allocation', 'Transfer');

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "PoLine"}'
WHERE
  jsonb ->> 'transactionType' = 'Encumbrance';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "Invoice"}'
WHERE
  jsonb ->> 'transactionType' IN ('Credit', 'Payment');
