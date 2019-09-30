-- https://www.postgresql.org/docs/10/functions-json.html
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.recalculate_totals() RETURNS TRIGGER AS $recalculate_totals$
  DECLARE
    fromBudget              jsonb;
    fromBudgetAllocated     decimal;
    fromBudgetAvailable     decimal;
    fromBudgetUnavailable   decimal;

    fromLedger              jsonb;
    fromLedgerAllocated     decimal;
    fromLedgerAvailable     decimal;
    fromLedgerUnavailable   decimal;

    toBudget                jsonb;
    toBudgetAllocated       decimal;
    toBudgetAvailable       decimal;
    toBudgetUnavailable     decimal;

    toLedger                jsonb;
    toLedgerAllocated       decimal;
    toLedgerAvailable       decimal;
    toLedgerUnavailable     decimal;

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
          SELECT INTO fromBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');

          fromBudgetAllocated = (SELECT COALESCE(fromBudget->>'allocated', '0'))::decimal - amount;
          fromBudgetAvailable = (SELECT COALESCE(fromBudget->>'available', '0'))::decimal - amount;
          fromBudgetUnavailable = (SELECT COALESCE(fromBudget->>'unavailable', '0'))::decimal + amount;
          newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');


          -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the source fund (fromFundId)
          SELECT INTO fromLedger (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledger  WHERE (jsonb->>'id' = (SELECT jsonb->>'ledgerId' FROM ${myuniversity}_${mymodule}.fund WHERE (jsonb->>'id' = fromBudget->>'fundId')));

          fromLedgerAllocated = (SELECT COALESCE(fromLedger->>'allocated', '0'))::decimal + amount;
          fromLedgerAvailable = (SELECT COALESCE(fromLedger->>'available', '0'))::decimal - amount;
          fromLedgerUnavailable = (SELECT COALESCE(fromLedger->>'unavailable', '0'))::decimal + amount;
          newLedgerValues = '{allocated,' || fromLedgerAllocated || ', available, ' || fromLedgerAvailable || ', unavailable, ' || fromLedgerUnavailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.ledger SET jsonb = jsonb || json_object(newLedgerValues)::jsonb WHERE (jsonb->>'id' = fromLedger->>'id');

        END IF;
        -- Update Budget identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
          SELECT INTO toBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'toFundId');
          IF (toBudget IS NULL) THEN
            RAISE EXCEPTION 'destination budget not found';
          END IF;

          toBudgetAllocated = (SELECT COALESCE(toBudget->>'allocated', '0'))::decimal + amount;
          toBudgetAvailable = (SELECT COALESCE(toBudget->>'available', '0'))::decimal + amount;
          newBudgetValues = '{allocated,' || toBudgetAllocated || ', available, ' || toBudgetAvailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'toFundId');

        -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
          SELECT INTO toLedger (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledger  WHERE (jsonb->>'id' = (SELECT jsonb->>'ledgerId' FROM ${myuniversity}_${mymodule}.fund WHERE (jsonb->>'id' = toBudget->>'fundId')));

          toLedgerAllocated = (SELECT COALESCE(toLedger->>'allocated', '0'))::decimal + amount;
          toLedgerAvailable = (SELECT COALESCE(toLedger->>'available', '0'))::decimal + amount;
          newLedgerValues = '{allocated,' || toLedgerAllocated || ', available, ' || toLedgerAvailable ||'}';

          UPDATE ${myuniversity}_${mymodule}.ledger SET jsonb = jsonb || json_object(newLedgerValues)::jsonb WHERE (jsonb->>'id' = toLedger->>'id');

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
