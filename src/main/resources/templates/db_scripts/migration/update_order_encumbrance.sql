-- Rollover encumbrances into the next fiscal year
UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb = jsonb_set(jsonb, '{encumbrance, reEncumber}', 'true', false)
WHERE jsonb ? 'encumbrance'
AND (jsonb::json -> 'encumbrance' ->> 'reEncumber')::boolean <> true;
