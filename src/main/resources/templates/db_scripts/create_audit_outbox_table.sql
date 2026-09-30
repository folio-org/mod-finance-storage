CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.outbox_event_log (
  event_id uuid NOT NULL PRIMARY KEY,
  entity_type text NOT NULL,
  action text NOT NULL,
  payload jsonb
);
