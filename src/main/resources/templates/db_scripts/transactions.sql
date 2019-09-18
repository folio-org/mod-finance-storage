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

    toBudgetRecord          record;
    toBudget                jsonb;
    toBudgetAllocated       decimal;
    toBudgetAvailable       decimal;
    toBudgetUnavailable     decimal;

    toLedgerRecord          record;
    toLedger                jsonb;
    toLedgerAllocated       decimal;
    toLedgerAvailable       decimal;
    toLedgerUnavailable     decimal;

    fundRecord              record;
    newBudgetValues         text[];
    newLedgerValues         text[];
    amount                  decimal;

    BEGIN
      amount = (NEW.jsonb->>'amount')::decimal;

      -- Recalculate Allocation
      IF (NEW.jsonb->>'transactionType' = 'Allocation') THEN
        --check if fromFundId exists
        IF (NEW.jsonb->'fromFundId' IS NOT NULL) THEN
          -- Update Budget identified by the transactions fiscal year (fiscalYearId) and source fund (fromFundId)
          SELECT INTO fromBudgetRecord * FROM  ${myuniversity}_${mymodule}.budget WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');
          fromBudget = fromBudgetRecord.jsonb::jsonb;

          fromBudgetAllocated = (SELECT COALESCE(fromBudget->>'allocated', '0'))::decimal - amount;
          fromBudgetAvailable = (SELECT COALESCE(fromBudget->>'available', '0'))::decimal - amount;
          fromBudgetUnavailable = (SELECT COALESCE(fromBudget->>'unavailable', '0'))::decimal + amount;
          newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');


          -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the source fund (fromFundId)
          SELECT INTO fundRecord * FROM ${myuniversity}_${mymodule}.fund WHERE (jsonb->>'id' = fromBudget->>'fundId');
          SELECT INTO fromLedgerRecord * FROM  ${myuniversity}_${mymodule}.ledger  WHERE (jsonb->>'id' = fundRecord.jsonb::jsonb->>'ledgerId');
          fromLedger = fromLedgerRecord.jsonb::jsonb;

          fromLedgerAllocated = (SELECT COALESCE(fromLedger->>'allocated', '0'))::decimal - amount;
          fromLedgerAvailable = (SELECT COALESCE(fromLedger->>'available', '0'))::decimal - amount;
          fromLedgerUnavailable = (SELECT COALESCE(fromLedger->>'unavailable', '0'))::decimal + amount;
          newLedgerValues = '{allocated,' || fromLedgerAllocated || ', available, ' || fromLedgerAvailable || ', unavailable, ' || fromLedgerUnavailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.ledger SET jsonb = jsonb || json_object(newLedgerValues)::jsonb WHERE (jsonb->>'id' = fromLedger->>'id');

        END IF;
        -- Update Budget identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
        -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)


      -- Recalculate Credit
      ELSIF (NEW.jsonb->>'transactionType' = 'Credit') THEN
        -- TODO: Credit recalculation

      -- Recalculate Encumbrance
      ELSIF (NEW.jsonb->>'transactionType' = 'Encumbrance') THEN
        -- TODO: Encumbrance recalculation

      -- Recalculate Payment
      ELSIF (NEW.jsonb->>'transactionType' = 'Payment') THEN
        -- TODO: Payment recalculation

      -- Recalculate Transfer
      ELSIF (NEW.jsonb->>'transactionType' = 'Transfer') THEN
        -- TODO: Transfer recalculation
      END IF;

        RETURN NULL;
    END;
$recalculate_totals$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
CREATE TRIGGER recalculate_totals AFTER INSERT ON "transaction" FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.recalculate_totals();
