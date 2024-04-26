DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.recalculate_totals();
CREATE INDEX IF NOT EXISTS transaction_transactiontype_simple_idx ON ${myuniversity}_${mymodule}.transaction USING btree ((jsonb->>'transactionType'));
