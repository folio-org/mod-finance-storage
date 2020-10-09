-- https://www.postgresql.org/docs/10/functions-json.html
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.recalculate_totals() RETURNS TRIGGER AS $recalculate_totals$
  DECLARE
    fromBudget                  jsonb;
    fromBudgetAllocated         decimal;
    fromBudgetAvailable         decimal;
    fromBudgetUnavailable       decimal;
    fromBudgetEncumbered        decimal;
    fromBudgetOverEncumbered    decimal;

    toBudget                    jsonb;
    toBudgetAllocated           decimal;
    toBudgetAvailable           decimal;

    newBudgetValues             text[];
    amount                      decimal;
    transactionType             text;

    BEGIN
      amount = (NEW.jsonb->>'amount')::decimal;
      transactionType = NEW.jsonb->>'transactionType';

      -- Recalculate Allocation
      IF (transactionType = 'Allocation') THEN
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

         fromBudgetAllocated = (fromBudget->>'allocated')::decimal - amount;
         newBudgetValues = '{allocated,' || fromBudgetAllocated || ', available, ' || fromBudgetAvailable ||'}';

         UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb
           WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'fromFundId');

        END IF;

          -- Update Budget identified by the transaction's fiscal year (fiscalYearId) and the destination fund (toFundId)
            SELECT INTO toBudget (jsonb::jsonb) FROM  ${myuniversity}_${mymodule}.budget WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'toFundId');
            IF (toBudget IS NULL) THEN
              RAISE EXCEPTION 'destination budget not found';
            END IF;

            toBudgetAvailable = (toBudget->>'available')::decimal + amount;
            toBudgetAllocated = (toBudget->>'allocated')::decimal + amount;
            newBudgetValues = '{allocated,' || toBudgetAllocated || ', available, ' || toBudgetAvailable ||'}';

            UPDATE ${myuniversity}_${mymodule}.budget SET jsonb = jsonb || json_object(newBudgetValues)::jsonb
            WHERE (fiscalYearId::text = NEW.jsonb->>'fiscalYearId' AND fundId::text = NEW.jsonb->>'toFundId');

      END IF;

        RETURN NULL;
    END;
$recalculate_totals$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS recalculate_totals on ${myuniversity}_${mymodule}."transaction";
CREATE TRIGGER recalculate_totals AFTER INSERT ON "transaction" FOR EACH ROW EXECUTE PROCEDURE ${myuniversity}_${mymodule}.recalculate_totals();
