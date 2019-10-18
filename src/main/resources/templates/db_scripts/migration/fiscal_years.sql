-- Rename fields

UPDATE ${myuniversity}_${mymodule}.fiscal_year
SET jsonb =
	jsonb #- '{startDate}' || jsonb_build_object('periodStart', jsonb::json ->> 'startDate')
	WHERE jsonb::json->>'startDate' iS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.fiscal_year
SET jsonb =
	jsonb #- '{endDate}' || jsonb_build_object('periodEnd', jsonb::json ->> 'endDate')
	WHERE jsonb::json ->> 'endDate' iS NOT NULL;
