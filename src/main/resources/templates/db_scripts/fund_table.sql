CREATE UNIQUE INDEX IF NOT EXISTS fund_code_unique_idx ON ${myuniversity}_${mymodule}.fund ((jsonb->>'code'));
