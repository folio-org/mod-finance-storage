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
    transactionType         text;

    BEGIN
      amount = (NEW.jsonb->>'amount')::decimal;
      transactionType = NEW.jsonb->>'transactionType';

      -- Recalculate Allocation / Transfer
      IF (transactionType = 'Allocation' OR transactionType = 'Transfer') THEN
        --check if fromFundId exists
        IF (NEW.jsonb->'fromFundId' IS NOT NULL) THEN
          -- Update Budget identified by the transactions fiscal year (fiscalYearId) and source fund (fromFundId)
          SELECT INTO fromBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');
          IF (fromBudget IS NULL) THEN
            RAISE EXCEPTION 'source budget not found';
          END IF;
          fromBudgetAvailable = (SELECT COALESCE(fromBudget->>'available', '0'))::decimal - amount;
          fromBudgetUnavailable = (SELECT COALESCE(fromBudget->>'unavailable', '0'))::decimal + amount;

          IF (transactionType = 'Allocation') THEN
            fromBudgetAllocated = (SELECT COALESCE(fromBudget->>'allocated', '0'))::decimal - amount;
            newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable ||'}';
          ELSIF (transactionType = 'Transfer') THEN
            newBudgetValues = '{available, ' || fromBudgetAvailable || ', unavailable, ' || fromBudgetUnavailable ||'}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb WHERE (jsonb->>'fiscalYearId' = NEW.jsonb->>'fiscalYearId' AND jsonb->>'fundId' = NEW.jsonb->>'fromFundId');


          -- Update Ledger identified by the transaction's fiscal year (fiscalYearId) and the source fund (fromFundId)
          SELECT INTO fromLedger (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.ledger  WHERE (jsonb->>'id' = (SELECT jsonb->>'ledgerId' FROM ${myuniversity}_${mymodule}.fund WHERE (jsonb->>'id' = fromBudget->>'fundId')));

          fromLedgerAvailable = (SELECT COALESCE(fromLedger->>'available', '0'))::decimal - amount;
          fromLedgerUnavailable = (SELECT COALESCE(fromLedger->>'unavailable', '0'))::decimal + amount;

          IF (transactionType = 'Allocation') THEN
            fromLedgerAllocated = (SELECT COALESCE(fromLedger->>'allocated', '0'))::decimal - amount;
            newLedgerValues = '{allocated,' || fromLedgerAllocated || ', available, ' || fromLedgerAvailable || ', unavailable, ' || fromLedgerUnavailable ||'}';
          ELSIF (transactionType = 'Transfer') THEN
            newLedgerValues = '{available, ' || fromLedgerAvailable || ', unavailable, ' || fromLedgerUnavailable ||'}';
          END IF;

          UPDATE ${myuniversity}_${mymodule}.ledger SET jsonb = jsonb || json_object(newLedgerValues)::jsonb WHERE (jsonb->>'id' = fromLedger->>'id');

        END IF;
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

      -- Recalculate Credit
      ELSIF (NEW.jsonb->>'transactionType' = 'Credit') THEN
        -- TODO: Credit recalculation

      -- Recalculate Encumbrance
      ELSIF (NEW.jsonb->>'transactionType' = 'Encumbrance') THEN
        -- TODO: Encumbrance recalculation

      -- Recalculate Payment
      ELSIF (NEW.jsonb->>'transactionType' = 'Payment') THEN
        -- TODO: Payment recalculation
      END IF;

        RETURN NULL;
    END;
$recalculate_totals$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
CREATE TRIGGER recalculate_totals AFTER INSERT ON "transaction" FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.recalculate_totals();

-- Rename fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
	jsonb #- '{awaitingPayment}' || jsonb_build_object('amountAwaitingPayment', jsonb::json -> 'awaitingPayment')
	WHERE jsonb::json ->> 'awaitingPayment' iS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
	jsonb #- '{encumbered}' || jsonb_build_object('initialAmountEncumbered', jsonb::json -> 'encumbered')
	WHERE jsonb::json ->> 'encumbered' iS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
	jsonb #- '{expenditures}' || jsonb_build_object('amountExpended', jsonb::json -> 'expenditures')
	WHERE jsonb::json ->> 'expenditures' iS NOT NULL;

-- Required fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
  jsonb || jsonb_build_object('initialAmountEncumbered', 0)
  WHERE jsonb::json ->> 'initialAmountEncumbered' iS NULL;

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
  jsonb || jsonb_build_object('amount', 0)
  WHERE jsonb::json ->> 'amount' iS NULL;

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
  jsonb || jsonb_build_object('currency', 'USD')
  WHERE jsonb::json ->> 'currency' iS NULL;

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
  jsonb || jsonb_build_object('fiscalYearId',
  (SELECT ${myuniversity}_${mymodule}.budget.fiscalYearId
  	FROM ${myuniversity}_${mymodule}.budget
  	WHERE ${myuniversity}_${mymodule}.budget.id::text
    = (SELECT (jsonb::json ->> 'budgetId')::text FROM ${myuniversity}_${mymodule}.transaction LIMIT 1)))
    WHERE jsonb::json ->> 'fiscalYearId' iS NULL;

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
  jsonb || jsonb_build_object('source', 'Manual')
  WHERE jsonb::json ->> 'source' iS NULL OR (jsonb::json->>'source' <> 'Credit'
  AND jsonb::json ->> 'source' <> 'Manual' AND jsonb::json->>'source' <> 'User'
  AND jsonb::json ->> 'source' <> 'Voucher');

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
  jsonb || jsonb_build_object('transactionType', 'Allocation')
  WHERE jsonb::json ->> 'transactionType' iS NULL OR (jsonb::json ->> 'transactionType' <> 'Allocation'
  AND jsonb::json ->> 'transactionType' <> 'Credit' AND jsonb::json ->> 'transactionType' <> 'Encumbrance'
  AND jsonb::json ->> 'transactionType' <> 'Payment' AND jsonb::json ->> 'transactionType' <> 'Transfer');

-- Update tags
UPDATE ${myuniversity}_${mymodule}.budget
SET jsonb =
   jsonb || jsonb_build_object('tags', jsonb_build_object('tagList', jsonb::json -> 'tags'))
   WHERE (jsonb::json #> '{tags, tagList}') iS NULL;

-- Remove fields

UPDATE ${myuniversity}_${mymodule}.transaction
SET jsonb =
    (
      jsonb - '{available, note, overcharge, timestamp, sourceId, budgetId}'::text[]
    );
