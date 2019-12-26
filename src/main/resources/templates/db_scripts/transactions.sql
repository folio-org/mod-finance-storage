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
          ELSIF (transactionType = 'Encumbrance') THEN
            fromBudgetEncumbered = (fromBudget->>'encumbered')::decimal + amount;
            --TODO: Budget overEncumbered will be calculated in a follow-on story
            newBudgetValues = '{available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable || ', encumbered, ' || fromBudgetEncumbered ||'}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb
            WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'fromFundId');


          -- Update LedgerFY identified by the transaction's fiscal year (fiscalYearId) and the source fund (fromFundId)
          SELECT INTO fromLedgerFY (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledgerFY AS ledgerFY
          WHERE (ledgerFY.ledgerId = (SELECT fund.ledgerId FROM ${myuniversity}_${mymodule}.fund AS fund WHERE (fund.id::text = fromBudget->>'fundId')))
          AND ledgerFY.fiscalYearId::text = fromBudget->>'fiscalYearId';

          IF (fromLedgerFY IS NULL) THEN
            RAISE EXCEPTION 'Ledger fiscal year for source ledger not found';
          END IF;

          fromLedgerFYAvailable = (fromLedgerFY->>'available')::decimal - amount;
          fromLedgerFYUnavailable = (fromLedgerFY->>'unavailable')::decimal + amount;

          IF (transactionType = 'Allocation') THEN
            fromLedgerFYAllocated = (fromLedgerFY->>'allocated')::decimal - amount;
            newLedgerValues = '{allocated,' || fromLedgerFYAllocated || ', available, ' || fromLedgerFYAvailable || '}';
          ELSEIF (transactionType = 'Transfer') THEN
            newLedgerValues = '{available, ' || fromLedgerFYAvailable ||'}';
          ELSIF (transactionType = 'Encumbrance') THEN
            newLedgerValues = '{available, ' || fromLedgerFYAvailable || ', unavailable, ' || fromLedgerFYUnavailable ||'}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.ledgerFY SET jsonb = jsonb || json_object(newLedgerValues)::jsonb
          WHERE (ledgerId::text = fromLedgerFY->>'ledgerId') AND (fiscalYearId::text = fromLedgerFY->>'fiscalYearId');

        END IF;

        -- update destination budget and ledgerFY only for operations: Allocation / Transfer
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
            SELECT INTO toLedgerFY (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledgerFY AS ledgerFY
             WHERE (ledgerFY.ledgerId = (SELECT ledgerId FROM ${myuniversity}_${mymodule}.fund WHERE (id::text = toBudget->>'fundId')))
             AND ledgerFY.fiscalYearId::text = toBudget->>'fiscalYearId';

            IF (toLedgerFY IS NULL) THEN
              RAISE EXCEPTION 'Ledger fiscal year for destination ledger not found';
            END IF;

            toLedgerFYAvailable = (toLedgerFY->>'available')::decimal + amount;
            IF (transactionType = 'Allocation') THEN
              toLedgerFYAllocated = (toLedgerFY->>'allocated')::decimal + amount;
              newLedgerValues = '{allocated,' || toLedgerFYAllocated || ', available, ' || toLedgerFYAvailable ||'}';
            ELSIF (transactionType = 'Transfer') THEN
              newLedgerValues = '{available, ' || toLedgerFYAvailable ||'}';
            END IF;

            UPDATE ${myuniversity}_${mymodule}.ledgerFY SET jsonb = jsonb || json_object(newLedgerValues)::jsonb
            WHERE (ledgerId::text = toLedgerFY->>'ledgerId') AND (fiscalYearId::text = toLedgerFY->>'fiscalYearId');

        END IF;
      END IF;

        RETURN NULL;
    END;
$recalculate_totals$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
CREATE TRIGGER recalculate_totals AFTER INSERT ON "transaction" FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.recalculate_totals();

-- Rename fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb_set(jsonb - '{awaitingPayment, encumbered, expenditures}'::text[], '{encumbrance}',
     jsonb_strip_nulls(jsonb_build_object('amountAwaitingPayment', jsonb::json -> 'awaitingPayment')
      || jsonb_build_object('initialAmountEncumbered', jsonb::json -> 'encumbered')
	    || jsonb_build_object('amountExpended', jsonb::json -> 'expenditures')))
WHERE
  NOT jsonb ? 'encumbrance';

-- Required fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb->'encumbrance' || jsonb_build_object('initialAmountEncumbered', 0)
WHERE
  NOT jsonb->'encumbrance' ? 'initialAmountEncumbered';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('currency', 'USD')
WHERE
  NOT jsonb ? 'currency';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('fiscalYearId',
  (SELECT ${myuniversity}_${mymodule}.budget.fiscalYearId
  	FROM ${myuniversity}_${mymodule}.budget
  	WHERE ${myuniversity}_${mymodule}.budget.id::text
    = (SELECT (jsonb::json ->> 'budgetId')::text FROM ${myuniversity}_${mymodule}.transaction LIMIT 1)))
WHERE NOT
  jsonb ?'fiscalYearId';

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('source', 'Manual')
WHERE
  NOT jsonb ? 'source'
  OR (jsonb::json->>'source' <> 'Credit' AND jsonb::json ->> 'source' <> 'Manual' AND jsonb::json->>'source' <> 'User'
  AND jsonb::json ->> 'source' <> 'Voucher');

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('transactionType', 'Allocation')
WHERE
  NOT jsonb ? 'transactionType'
  OR (jsonb::json ->> 'transactionType' <> 'Allocation' AND jsonb::json ->> 'transactionType' <> 'Credit'
  AND jsonb::json ->> 'transactionType' <> 'Encumbrance'
  AND jsonb::json ->> 'transactionType' <> 'Payment' AND jsonb::json ->> 'transactionType' <> 'Transfer');

-- Update tags

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb || jsonb_build_object('tags', jsonb_build_object('tagList', jsonb::json -> 'tags'))
WHERE
  jsonb::json #> '{tags, tagList}' iS NULL AND jsonb ? 'tags';

-- Remove fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET
  jsonb = jsonb - '{available, note, overcharge, timestamp, sourceId, budgetId}'::text[];
