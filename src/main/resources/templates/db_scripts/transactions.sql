-- https://www.postgresql.org/docs/10/functions-json.html
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.recalculate_totals() RETURNS TRIGGER AS $recalculate_totals$
  DECLARE
    fromBudget                  jsonb;
    fromBudgetAllocated         decimal;
    fromBudgetAvailable         decimal;
    fromBudgetUnavailable       decimal;
    fromBudgetEncumbered        decimal;
    fromBudgetOverEncumbered    decimal;

    fromLedger                  jsonb;
    fromLedgerAllocated         decimal;
    fromLedgerAvailable         decimal;
    fromLedgerUnavailable       decimal;

    toBudget                    jsonb;
    toBudgetAllocated           decimal;
    toBudgetAvailable           decimal;
    toBudgetUnavailable         decimal;

    toLedger                    jsonb;
    toLedgerAllocated           decimal;
    toLedgerAvailable           decimal;
    toLedgerUnavailable         decimal;

    newBudgetValues             text[];
    newLedgerValues             text[];
    amount                      decimal;
    transactionType             text;

    BEGIN
      amount = (NEW.jsonb->>'amount')::decimal;
      transactionType = NEW.jsonb->>'transactionType';

      -- Recalculate Allocation / Transfer / Encumbrance
      IF (transactionType = 'Allocation' OR transactionType = 'Transfer' OR transactionType = 'Encumbrance') THEN
        -- abort calculations if fromFundId not specified
        IF ((NEW.jsonb->'fromFundId' IS NULL) AND (transactionType = 'Encumbrance')) THEN
          RAISE EXCEPTION 'fromFundId is not specified for Encumbrance';
        END IF;
        -- check if fromFundId exists
        IF (NEW.jsonb->'fromFundId' IS NOT NULL) THEN
          -- Update Budget identified by the transactions fiscal year (fiscalYearId) and source fund (fromFundId)
          SELECT INTO fromBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');
          IF (fromBudget IS NULL) THEN
            RAISE EXCEPTION 'source budget not found';
          END IF;
          --
          fromBudgetAvailable = (SELECT COALESCE(fromBudget->>'available', '0'))::decimal - amount;
          fromBudgetUnavailable = (SELECT COALESCE(fromBudget->>'unavailable', '0'))::decimal + amount;

          IF (transactionType = 'Allocation') THEN
            fromBudgetAllocated = (SELECT COALESCE(fromBudget->>'allocated', '0'))::decimal - amount;
            newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable ||'}';
          ELSIF (transactionType = 'Transfer') THEN
            newBudgetValues = '{available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable ||'}';
          ELSIF (transactionType = 'Encumbrance') THEN
            fromBudgetEncumbered = (SELECT COALESCE(fromBudget->>'encumbered', '0'))::decimal + amount;
            --TODO: Budget overEncumbered will be calculated in a follow-on story
            newBudgetValues = '{available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable || ', encumbered, ' || fromBudgetEncumbered ||'}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');


          -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the source fund (fromFundId)
          SELECT INTO fromLedger (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledger  WHERE (jsonb->>'id' = (SELECT jsonb->>'ledgerId' FROM ${myuniversity}_${mymodule}.fund WHERE (jsonb->>'id' = fromBudget->>'fundId')));

          fromLedgerAvailable = (SELECT COALESCE(fromLedger->>'available', '0'))::decimal - amount;
          fromLedgerUnavailable = (SELECT COALESCE(fromLedger->>'unavailable', '0'))::decimal + amount;

          IF (transactionType = 'Allocation') THEN
            fromLedgerAllocated = (SELECT COALESCE(fromLedger->>'allocated', '0'))::decimal - amount;
            newLedgerValues = '{allocated,' || fromLedgerAllocated || ', available, ' || fromLedgerAvailable || '}';
          ELSIF (transactionType = 'Transfer' OR transactionType = 'Encumbrance') THEN
            newLedgerValues = '{available, ' || fromLedgerAvailable || ', unavailable, ' || fromLedgerUnavailable ||'}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.ledger SET jsonb = jsonb || json_object(newLedgerValues)::jsonb WHERE (jsonb->>'id' = fromLedger->>'id');

        END IF;

        -- update destination budget and ledger only for operations: Allocation / Transfer
        IF (transactionType = 'Allocation' OR transactionType = 'Transfer') THEN
          -- Update Budget identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
            SELECT INTO toBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'toFundId');
            IF (toBudget IS NULL) THEN
              RAISE EXCEPTION 'destination budget not found';
            END IF;

            toBudgetAvailable = (SELECT COALESCE(toBudget->>'available', '0'))::decimal + amount;
            IF (transactionType = 'Allocation') THEN
              toBudgetAllocated = (SELECT COALESCE(toBudget->>'allocated', '0'))::decimal + amount;
              newBudgetValues = '{allocated,' || toBudgetAllocated || ', available, ' || toBudgetAvailable ||'}';
            ELSIF (transactionType = 'Transfer') THEN
              newBudgetValues = '{available, ' || toBudgetAvailable ||'}';
            END IF;

            UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'toFundId');

          -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
            SELECT INTO toLedger (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledger  WHERE (jsonb->>'id' = (SELECT jsonb->>'ledgerId' FROM ${myuniversity}_${mymodule}.fund WHERE (jsonb->>'id' = toBudget->>'fundId')));

            toLedgerAvailable = (SELECT COALESCE(toLedger->>'available', '0'))::decimal + amount;
            IF (transactionType = 'Allocation') THEN
              toLedgerAllocated = (SELECT COALESCE(toLedger->>'allocated', '0'))::decimal + amount;
              newLedgerValues = '{allocated,' || toLedgerAllocated || ', available, ' || toLedgerAvailable ||'}';
            ELSIF (transactionType = 'Transfer') THEN
              newLedgerValues = '{available, ' || toLedgerAvailable ||'}';
            END IF;

            UPDATE ${myuniversity}_${mymodule}.ledger SET jsonb = jsonb || json_object(newLedgerValues)::jsonb WHERE (jsonb->>'id' = toLedger->>'id');

        END IF;
      END IF;

        RETURN NULL;
    END;
$recalculate_totals$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
CREATE TRIGGER recalculate_totals AFTER INSERT ON "transaction" FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.recalculate_totals();
