-- Rename fields

UPDATE ${myuniversity}_${mymodule}.fund
SET
  jsonb = jsonb #- '{allocationFrom}' || jsonb_build_object('allocatedFromIds', jsonb::json -> 'allocationFrom')
WHERE
  jsonb ? 'allocationFrom';

UPDATE ${myuniversity}_${mymodule}.fund
SET
  jsonb = jsonb #- '{allocationTo}' || jsonb_build_object('allocatedToIds', jsonb::json -> 'allocationTo')
WHERE
  jsonb ? 'allocationTo';

UPDATE ${myuniversity}_${mymodule}.fund
SET
  jsonb = jsonb || jsonb_build_object('tags', jsonb_build_object('tagList', jsonb::json -> 'tags'))
WHERE
  jsonb::json #> '{tags, tagList}' iS NULL AND jsonb ? 'tags';

