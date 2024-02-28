DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.recalculate_totals();

ALTER TABLE ${myuniversity}_${mymodule}."transaction" DROP CONSTRAINT IF EXISTS transaction_encumbrance_unique;
ALTER TABLE ${myuniversity}_${mymodule}."transaction" ADD CONSTRAINT transaction_encumbrance_unique UNIQUE USING INDEX transaction_encumbrance_idx_unique;

