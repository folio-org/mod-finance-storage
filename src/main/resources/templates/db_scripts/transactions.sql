-- https://www.postgresql.org/docs/10/functions-json.html
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.recalculate_totals() RETURNS TRIGGER AS $recalculate_totals$
  DECLARE
    fromBudgetRecord        record;
    fromBudget              jsonb;
    fromBudgetAllocated     decimal;
    fromBudgetAvailable     decimal;
    fromBudgetUnavailable   decimal;

    fromLedgerRecord        record;
    fromLedger              jsonb;
    fromLedgerAllocated     decimal;
    fromLedgerAvailable     decimal;
    fromLedgerUnavailable   decimal;

    toBudgetRecord        record;
    toBudget              jsonb;
    toBudgetAllocated     decimal;
    toBudgetAvailable     decimal;
    toBudgetUnavailable   decimal;

    toLedgerRecord        record;
    toLedger              jsonb;
    toLedgerAllocated     decimal;
    toLedgerAvailable     decimal;
    toLedgerUnavailable   decimal;

    newBudgetValues         text[];
    newLedgerValues         text[];

    BEGIN
      -- Recalculate Allocation
      IF (NEW.jsonb->>'transactionType' = 'Allocation') THEN
        --check if fromFundId exists
        IF (NEW.jsonb->'fromFundId' IS NOT NULL) THEN
          -- Update Budget identified by the transactions fiscal year (fiscalYearId) and source fund (fromFundId)
          SELECT INTO fromBudgetRecord * FROM  ${myuniversity}_${mymodule}.budget WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');
          fromBudget = fromBudgetRecord.jsonb::jsonb;

          fromBudgetUnavailable = (SELECT COALESCE(fromBudget->>'allocated', '0'))::decimal - (NEW.jsonb->>'amount')::decimal;
          fromBudgetUnavailable = (SELECT COALESCE(fromBudget->>'available', '0'))::decimal - (NEW.jsonb->>'amount')::decimal;
          fromBudgetUnavailable = (SELECT COALESCE(fromBudget->>'unavailable', '0'))::decimal + (NEW.jsonb->>'amount')::decimal;
          newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');

          -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the source fund (fromFundId)
          SELECT INTO fromLedgerRecord * FROM  ${myuniversity}_${mymodule}.ledger WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'toFundId');
          fromBudget = fromBudgetRecord.jsonb::jsonb;

          fromBudgetUnavailable = (SELECT COALESCE(fromLedger->>'allocated', '0'))::decimal - (NEW.jsonb->>'amount')::decimal;
          fromBudgetUnavailable = (SELECT COALESCE(fromLedger->>'available', '0'))::decimal - (NEW.jsonb->>'amount')::decimal;
          fromBudgetUnavailable = (SELECT COALESCE(fromLedger->>'unavailable', '0'))::decimal + (NEW.jsonb->>'amount')::decimal;
          newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');

        END IF;
        -- Update Budget identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
        -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the source fund (toFundId)
      END IF;



        -- Recalculate Credit
        --ELSIF (NEW.jsonb->>transactionType = 'Credit') THEN
            -- TODO: Credit recalculation

        -- Recalculate Encumbrance
        --ELSIF (NEW.jsonb->>transactionType = 'Encumbrance') THEN
            -- TODO: Encumbrance recalculation

        -- Recalculate Payment
        --ELSIF (NEW.jsonb->>transactionType = 'Payment') THEN
            -- TODO: Payment recalculation

        -- Recalculate Transfer
        --ELSIF (NEW.jsonb->>transactionType = 'Transfer') THEN
            -- TODO: Transfer recalculation

        RETURN NULL;
    END;
$recalculate_totals$ LANGUAGE plpgsql;

CREATE TRIGGER ${myuniversity}_${mymodule}_recalculate_totals AFTER INSERT OR UPDATE ON "transaction" FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.recalculate_totals();
