-- This script replaces null values by 0.0 for amount and initialAmountEncumbered in transactions and encumbered in budgets.
-- Budgets' encumbered values might have to be fixed by hand afterwards.
-- This is meant as a way to partially repair damage caused by MODFISTO-298, and should be executed manually.

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb_set(jsonb || jsonb_build_object('amount', 0.0), '{encumbrance, initialAmountEncumbered}', '0.0')
WHERE
  jsonb->>'amount' IS NULL;

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb || jsonb_build_object('encumbered', 0.0)
WHERE
  jsonb->>'encumbered' IS NULL;
