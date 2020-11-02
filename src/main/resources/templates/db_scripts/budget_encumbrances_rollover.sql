CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA public;
CREATE OR REPLACE FUNCTION build_budget(_budget jsonb, _fund jsonb, _rollover_record jsonb, _fiscal_year jsonb) RETURNS jsonb AS $$
    DECLARE
        budget_rollover                 jsonb;
        allocated                       decimal;
        metadata                        jsonb;
    BEGIN
         SELECT br INTO budget_rollover FROM jsonb_array_elements(_rollover_record->'budgetsRollover') br
                     WHERE br->>'fundTypeId'=_fund->>'fundTypeId' OR (NOT br ? 'fundTypeId' AND NOT _fund ? 'fundTypeId');

        allocated := (_budget->>'allocated')::decimal +
            CASE
                WHEN budget_rollover ? 'adjustAllocation' AND (budget_rollover->>'rolloverAllocation')::boolean
                THEN (_budget->>'allocated')::decimal*(budget_rollover->>'adjustAllocation')::decimal/100
                ELSE 0
            END;

        metadata := _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text, 'updatedDate', date_trunc('milliseconds', clock_timestamp())::text);

        RETURN (_budget - 'id') || jsonb_build_object
            (
                'fiscalYearId', _rollover_record->>'toFiscalYearId',
                'name', (_fund->>'code') || '-' ||  (_fiscal_year->>'code'),
                'allocated', allocated,
                'metadata', metadata,
                'budgetStatus', 'Active',
                'allowableEncumbrance', budget_rollover->>'allowableEncumbrance',
                'allowableExpenditure', budget_rollover->>'allowableExpenditure',
                'unavailable', 0,
                'netTransfers', 0,
                'awaitingPayment', 0,
                'encumbered', 0,
                'expenditures', 0,
                'overEncumbrance', 0,
                'overExpended', 0
            );
    END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.budget_encumbrances_rollover(_ledger_id TEXT) RETURNS VOID as $$
    DECLARE
            toFiscalYear					jsonb;
            fromFiscalYear					jsonb;
            rollover_record                  jsonb;
    BEGIN


        SELECT INTO rollover_record (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.rollover
                        WHERE _ledger_id = ${myuniversity}_${mymodule}.rollover.jsonb->>'ledgerId';
        SELECT INTO toFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE rollover_record->>'toFiscalYearId'=jsonb->>'id';
        SELECT INTO fromFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE rollover_record->>'fromFiscalYearId'=jsonb->>'id';

        INSERT INTO ${myuniversity}_${mymodule}.budget 
            (
                SELECT public.uuid_generate_v4(), build_budget(budget.jsonb, fund.jsonb, rollover_record, toFiscalYear)
                FROM ${myuniversity}_${mymodule}.budget AS budget
                INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id=budget.fundId
                WHERE budget.jsonb->>'fiscalYearId'=rollover_record->>'fromFiscalYearId' AND fund.jsonb->>'ledgerId'=_ledger_id
            )
            ON CONFLICT (lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fundId'::text)), lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fiscalYearId'::text)))
                 DO UPDATE SET jsonb=${myuniversity}_${mymodule}.budget.jsonb || jsonb_build_object('allocated', (EXCLUDED.jsonb->>'allocated')::decimal,
                                                                                                    'metadata', ${myuniversity}_${mymodule}.budget.jsonb->'metadata' || jsonb_build_object('updatedDate', date_trunc('milliseconds', clock_timestamp())::text));
                     
         INSERT INTO ${myuniversity}_${mymodule}.transaction
            (
                SELECT public.uuid_generate_v4(), jsonb_build_object('toFundId', budget_after.jsonb->>'fundId', 'fiscalYearId', rollover_record->>'toFiscalYearId', 'transactionType', 'Allocation',
                                                             'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget_after.jsonb->>'allocated')::decimal - (budget_before.jsonb->>'allocated')::decimal,
                                                             'metadata', rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text, 'updatedDate', date_trunc('milliseconds', clock_timestamp())::text))
                FROM ${myuniversity}_${mymodule}.budget AS budget_after
                INNER JOIN ${myuniversity}_${mymodule}.budget AS budget_before ON budget_after.fundId=budget_before.fundId
                WHERE budget_before.jsonb->>'fiscalYearId'=rollover_record->>'fromFiscalYearId' AND budget_after.jsonb->>'fiscalYearId'=rollover_record->>'toFiscalYearId' AND budget_after.jsonb->>'allocated'<>budget_before.jsonb->>'allocated'
            );

    END;
$$ LANGUAGE plpgsql;