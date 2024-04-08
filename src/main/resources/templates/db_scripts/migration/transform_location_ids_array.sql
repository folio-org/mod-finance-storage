-- Transform locationIds arrays to array of objects with locationId field inside them
UPDATE ${myuniversity}_${mymodule}.fund
SET jsonb = jsonb - 'locationIds' || jsonb_build_object(
  'locations',
  CASE WHEN jsonb->>'locationIds' = '[]' THEN '[]' ELSE
  (SELECT jsonb_agg(jsonb_build_object('locationId', value)) from jsonb_array_elements(jsonb->'locationIds')) END
  )
WHERE jsonb ? 'locationIds';
