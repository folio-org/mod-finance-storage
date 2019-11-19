-- Rename fields

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb #- '{limitEncPercent}' || jsonb_build_object('allowableEncumbrance', jsonb::json ->> 'limitEncPercent')
WHERE
  jsonb ? 'limitEncPercent';

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb #- '{limitExpPercent}' || jsonb_build_object('allowableExpenditure', jsonb::json ->> 'limitExpPercent')
WHERE
  jsonb ? 'limitExpPercent';

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb #- '{allocation}' || jsonb_build_object('allocated', jsonb::json ->> 'allocation')
WHERE
  jsonb ? 'allocation';

-- Change type of required fields

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb || jsonb_build_object('budgetStatus', 'Active')
WHERE
  NOT jsonb ? 'budgetStatus' OR (jsonb::json ->> 'budgetStatus' <> 'Active'
  AND jsonb::json ->> 'budgetStatus' <> 'Frozen' AND jsonb::json ->> 'budgetStatus' <> 'Planned'
  AND jsonb::json ->> 'budgetStatus' <> 'Closed');

-- Change type of non-required fields

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb || jsonb_build_object('tags', jsonb_build_object('tagList', jsonb::json -> 'tags'))
WHERE
  jsonb::json #> '{tags, tagList}' iS NULL AND jsonb ? 'tags';

-- Make fields required

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb || jsonb_build_object('allocated', 0)
WHERE
  NOT jsonb ? 'allocated';

-- Remove fields

UPDATE ${myuniversity}_${mymodule}.budget
SET
  jsonb = jsonb - '{code}'::text[];



