-- https://www.postgresql.org/docs/10/functions-json.html
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.recalculate_totals() RETURNS TRIGGER AS $recalculate_totals$
  DECLARE
    fromBudget                  jsonb;
    fromBudgetAllocated         decimal;
    fromBudgetAvailable         decimal;
    fromBudgetUnavailable       decimal;
    fromBudgetEncumbered        decimal;
    fromBudgetOverEncumbered    decimal;

    fromLedgerFY                  jsonb;
    fromLedgerFYAllocated         decimal;
    fromLedgerFYAvailable         decimal;
    fromLedgerFYUnavailable       decimal;

    toBudget                    jsonb;
    toBudgetAllocated           decimal;
    toBudgetAvailable           decimal;

    toLedgerFY                    jsonb;
    toLedgerFYAllocated           decimal;
    toLedgerFYAvailable           decimal;

    newBudgetValues             text[];
    newLedgerFYValues             text[];
    amount                      decimal;
    transactionType             text;

    BEGIN
      amount = (NEW.jsonb->>'amount')::decimal;
      transactionType = NEW.jsonb->>'transactionType';

      -- Recalculate Allocation / Transfer
      IF (transactionType = 'Allocation' OR transactionType = 'Transfer') THEN
        -- check if fromFundId exists
        IF (NEW.jsonb->'fromFundId' IS NOT NULL) THEN
          -- Update Budget identified by the transactions fiscal year (fiscalYearId) and source fund (fromFundId)
          SELECT INTO fromBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget
            WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'fromFundId');
          IF (fromBudget IS NULL) THEN
            RAISE EXCEPTION 'source budget not found';
          END IF;
          --
          fromBudgetAvailable = (fromBudget->>'available')::decimal - amount;
          fromBudgetUnavailable = (fromBudget->>'unavailable')::decimal + amount;

          IF (transactionType = 'Allocation') THEN
            fromBudgetAllocated = (fromBudget->>'allocated')::decimal - amount;
            newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable ||'}';
          ELSIF (transactionType = 'Transfer') THEN
            newBudgetValues = '{available, ' || fromBudgetAvailable || '}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb
            WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'fromFundId');


          -- Update LedgerFY identified by the transaction's fiscal year (fiscalYearId) and the source fund (fromFundId)
          SELECT INTO fromLedgerFY (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledgerFy AS ledger_fy
          WHERE (ledger_fy.ledgerId = (SELECT fund.ledgerId FROM ${myuniversity}_${mymodule}.fund AS fund WHERE (fund.id::text = fromBudget->>'fundId')))
          AND ledger_fy.fiscalYearId::text = fromBudget->>'fiscalYearId';

          IF (fromLedgerFY IS NULL) THEN
            RAISE EXCEPTION 'Ledger fiscal year for source ledger not found';
          END IF;

          fromLedgerFYAvailable = (fromLedgerFY->>'available')::decimal - amount;
          fromLedgerFYUnavailable = (fromLedgerFY->>'unavailable')::decimal + amount;

          IF (transactionType = 'Allocation') THEN
            fromLedgerFYAllocated = (fromLedgerFY->>'allocated')::decimal - amount;
            newLedgerFYValues = '{allocated,' || fromLedgerFYAllocated || ', available, ' || fromLedgerFYAvailable || '}';
          ELSEIF (transactionType = 'Transfer') THEN
            newLedgerFYValues = '{available, ' || fromLedgerFYAvailable ||'}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.ledgerFy SET jsonb = jsonb || json_object(newLedgerFYValues)::jsonb
          WHERE (ledgerId::text = fromLedgerFY->>'ledgerId') AND (fiscalYearId::text = fromLedgerFY->>'fiscalYearId');

        END IF;

        -- update destination budget and ledgerFy only for operations: Allocation / Transfer
        IF (transactionType = 'Allocation' OR transactionType = 'Transfer') THEN
          -- Update Budget identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
            SELECT INTO toBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'toFundId');
            IF (toBudget IS NULL) THEN
              RAISE EXCEPTION 'destination budget not found';
            END IF;

            toBudgetAvailable = (toBudget->>'available')::decimal + amount;
            IF (transactionType = 'Allocation') THEN
              toBudgetAllocated = (toBudget->>'allocated')::decimal + amount;
              newBudgetValues = '{allocated,' || toBudgetAllocated || ', available, ' || toBudgetAvailable ||'}';
            ELSIF (transactionType = 'Transfer') THEN
              newBudgetValues = '{available, ' || toBudgetAvailable ||'}';
            END IF;

            UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb
            WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'toFundId');

          -- Update LedgerFY identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
            SELECT INTO toLedgerFY (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledgerFy AS ledger_fy
             WHERE (ledger_fy.ledgerId = (SELECT ledgerId FROM ${myuniversity}_${mymodule}.fund WHERE (id::text = toBudget->>'fundId')))
             AND ledger_fy.fiscalYearId::text = toBudget->>'fiscalYearId';

            IF (toLedgerFY IS NULL) THEN
              RAISE EXCEPTION 'Ledger fiscal year for destination ledger not found';
            END IF;

            toLedgerFYAvailable = (toLedgerFY->>'available')::decimal + amount;
            IF (transactionType = 'Allocation') THEN
              toLedgerFYAllocated = (toLedgerFY->>'allocated')::decimal + amount;
              newLedgerFYValues = '{allocated,' || toLedgerFYAllocated || ', available, ' || toLedgerFYAvailable ||'}';
            ELSIF (transactionType = 'Transfer') THEN
              newLedgerFYValues = '{available, ' || toLedgerFYAvailable ||'}';
            END IF;

            UPDATE ${myuniversity}_${mymodule}.ledgerFy SET jsonb = jsonb || json_object(newLedgerFYValues)::jsonb
            WHERE (ledgerId::text = toLedgerFY->>'ledgerId') AND (fiscalYearId::text = toLedgerFY->>'fiscalYearId');

        END IF;
      END IF;

        RETURN NULL;
    END;
$recalculate_totals$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
CREATE TRIGGER recalculate_totals AFTER INSERT ON "transaction" FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.recalculate_totals();
