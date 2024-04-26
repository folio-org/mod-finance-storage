CREATE INDEX IF NOT EXISTS transaction_transactiontype_simple_idx ON ${myuniversity}_${mymodule}.transaction USING btree ((jsonb->>'transactionType'));
