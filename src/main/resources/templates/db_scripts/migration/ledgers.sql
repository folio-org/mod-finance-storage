-- Change type of required fields

UPDATE ${myuniversity}_${mymodule}.ledger
SET
  jsonb = jsonb || jsonb_build_object('ledgerStatus', 'Active')
WHERE
  NOT jsonb ?'ledgerStatus'
  OR (jsonb::json ->> 'ledgerStatus' <> 'Active' AND jsonb::json ->> 'ledgerStatus' <> 'Inactive' AND jsonb::json ->> 'ledgerStatus' <> 'Frozen');

-- Remove fields

UPDATE ${myuniversity}_${mymodule}.ledger
SET
  jsonb = jsonb - '{periodStart, periodEnd, fiscalYears}'::text[];
