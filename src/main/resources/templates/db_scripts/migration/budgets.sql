-- Rename fields

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
	jsonb #- '{limitEncPercent}' || jsonb_build_object('allowableEncumbrance', jsonb::json ->> 'limitEncPercent')
	WHERE jsonb::json->>'limitEncPercent' iS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
	jsonb #- '{limitExpPercenet}' || jsonb_build_object('allowableExpenditure', jsonb::json ->> 'limitExpPercenet')
	WHERE jsonb::json ->> 'limitExpPercenet' iS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
	jsonb #- '{allocation}' || jsonb_build_object('allocated', jsonb::json ->> 'allocation')
	WHERE jsonb::json ->> 'allocation' iS NOT NULL;

-- Change type of required fields

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
  jsonb || jsonb_build_object('budgetStatus', 'Active')
  WHERE jsonb::json ->> 'budgetStatus' IS NULL OR (jsonb::json ->> 'budgetStatus' <> 'Active'
  AND jsonb::json ->> 'budgetStatus' <> 'Frozen' AND jsonb::json ->> 'budgetStatus' <> 'Planned'
  AND jsonb::json ->> 'budgetStatus' <> 'Closed');

-- Change type of non-required fields

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
   jsonb || jsonb_build_object('tags', jsonb_build_object('tagList', jsonb::json -> 'tags'))
   WHERE (jsonb::json #> '{tags, tagList}') iS NULL;

-- Make fields required

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
  jsonb || jsonb_build_object('allocated', 0)
  WHERE jsonb::json ->> 'allocated' iS NULL;

-- Remove fields

UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
    (
      jsonb - '{code}'::text[]
    );



