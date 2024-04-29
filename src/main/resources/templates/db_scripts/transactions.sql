DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.recalculate_totals();

-- these indices are also created in simple_transaction_indices.sql
CREATE INDEX IF NOT EXISTS transaction_transactiontype_simple_idx ON ${myuniversity}_${mymodule}.transaction USING btree ((jsonb->>'transactionType'));
CREATE INDEX IF NOT EXISTS transaction_sourcepurchaseorderid_simple_idx ON ${myuniversity}_${mymodule}.transaction USING btree ((jsonb->'encumbrance'->>'sourcePurchaseOrderId'));
CREATE INDEX IF NOT EXISTS transaction_sourcepolineid_simple_idx ON ${myuniversity}_${mymodule}.transaction USING btree ((jsonb->'encumbrance'->>'sourcePoLineId'));
