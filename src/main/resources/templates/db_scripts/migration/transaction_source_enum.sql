UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || '{"source": "User"}'
WHERE
  (jsonb ->> 'source') = 'Manual';

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
