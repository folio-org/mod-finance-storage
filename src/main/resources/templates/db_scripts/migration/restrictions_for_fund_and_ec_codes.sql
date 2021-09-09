-- Replace colon ":" with hyphen "-" for fund codes and expense class codes
-- Replace empty expense class codes with the expense class name

UPDATE ${myuniversity}_${mymodule}.fund
SET jsonb = jsonb || jsonb_build_object('code', replace(jsonb->>'code', ':', '-'));

UPDATE ${myuniversity}_${mymodule}.expense_class
SET jsonb = jsonb || jsonb_build_object('code', jsonb->>'name')
WHERE NOT jsonb ? 'code' OR jsonb->>'code' = '';

UPDATE ${myuniversity}_${mymodule}.expense_class
SET jsonb = jsonb || jsonb_build_object('code', replace(jsonb->>'code', ':', '-'));

