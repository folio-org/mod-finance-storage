/*
    Entry point - budget_encumbrances_rollover(_ledger_id TEXT) function

    #1 Retrieve rollover record
    #2 Create budgets using build_budget() function for the toFiscalYearId and every fund related to the ledger,
        if corresponding budget has already been created (ON CONFLICT) then update existed budget with new allocated, available, netTransfers, metadata values
    #3  Create allocation for every difference between budget.allocated and sum of corresponding allocations amount
    #4  Create transfer for every difference between budget.netTransfers and sum of corresponding transfers amount
    #5  Call rollover_order(_order_id text, _rollover_record jsonb) function for every order id ordered by the lowest creation date of related encumbrances
    For every order id
    #6 Check if there is any encumbrance that need to be rollovered for ledger related to order for which the rollover has not been completed yet
    #7 If #6 is true than create rollover error record
    #8 if #6 is false then check if rollover restrictEncumbrance setting is true and there is any sum of expected encumbrances grouped by fromFundId greater than corresponding budget remaining amount
    #9 If #8 is true then create corresponding rollover error
    #10 if #8 is false create encumbrances with amount non-zero amount calculated with calculate_planned_encumbrance_amount(_transaction jsonb, _rollover_record jsonb) function
    #11 update budget available, unavailable, encumbered, overEncumbrance by sum of encumbrances amount created on #10 step
 */
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA public;
-- Map encumbrance with corresponding encumbranceRollover item, calculate expected encumbrance amount based on that item
CREATE OR REPLACE FUNCTION public.calculate_planned_encumbrance_amount(_transaction jsonb, _rollover_record jsonb) RETURNS decimal as $$
	DECLARE
		amount decimal DEFAULT 0;
		encumbrance_rollover jsonb DEFAULT null;
	BEGIN
		IF
		  _transaction->'encumbrance'->>'orderType'='Ongoing' AND (_transaction->'encumbrance'->>'subscription')::boolean
		THEN
			SELECT INTO encumbrance_rollover (er::jsonb) FROM jsonb_array_elements(_rollover_record->'encumbrancesRollover') er WHERE er->>'orderType'='Ongoing-Subscription';
		ELSIF
			_transaction->'encumbrance'->>'orderType'='Ongoing'
		THEN
			SELECT INTO encumbrance_rollover (er::jsonb) FROM jsonb_array_elements(_rollover_record->'encumbrancesRollover') er WHERE er->>'orderType'='Ongoing';
		ELSIF
			_transaction->'encumbrance'->>'orderType'='One-Time'
		THEN
			SELECT INTO encumbrance_rollover (er::jsonb) FROM jsonb_array_elements(_rollover_record->'encumbrancesRollover') er WHERE er->>'orderType'='One-time';
		END IF;

		IF
			encumbrance_rollover->>'basedOn'='Expended'
		THEN
			amount:= (_transaction->'encumbrance'->>'amountExpended')::decimal + (_transaction->'encumbrance'->>'amountExpended')::decimal * (encumbrance_rollover->>'increaseBy')::decimal/100;
		ELSE
			amount:= (_transaction->>'amount')::decimal + (_transaction->>'amount')::decimal * (encumbrance_rollover->>'increaseBy')::decimal/100;
		END IF;
		RETURN amount;
	END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.rollover_order(_order_id text, _rollover_record jsonb) RETURNS VOID as $$

    BEGIN
        IF
            -- #6
            EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.transaction tr
                  LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                  LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_status rollover_status ON fund.ledgerId::text = rollover_status.jsonb->>'ledgerId'
                  WHERE fund.ledgerId::text<>_rollover_record->>'ledgerId' AND rollover_status.jsonb->>'toFiscalYearId' = _rollover_record->>'toFiscalYearId' AND
                           (NOT rollover_status.jsonb ? 'overallStatus' OR rollover_status.jsonb->>'overallStatus'='Not Started' OR rollover_status.jsonb->>'overallStatus'='In Progress')
                            AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id)
        THEN
            -- #7
            INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_errors (id, jsonb) 
                SELECT public.uuid_generate_v4(), jsonb_build_object
                (
                    'ledgerId', _rollover_record->>'ledgerId',
                    'toFiscalYearId', _rollover_record->>'toFiscalYearId',
                    'errorType', 'ORDER',
                    'failedAction', 'Create encumbrance',
                    'errorMessage', 'Part of the encumbrances belong to the ledger, which has not been rollovered',
                    'details', jsonb_build_object
                    (
                        'purchaseOrderId', _order_id,
                        'poLineId', tr.jsonb->'encumbrance'->>'sourcePoLineId',
                        'amount', public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record),
                        'fundId', tr.fromFundId::text						
                    )
                )  
                FROM ${myuniversity}_${mymodule}.transaction tr
                  LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                  LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_status rollover_status ON fund.ledgerId::text = rollover_status.jsonb->>'ledgerId'
                  WHERE fund.ledgerId::text<>_rollover_record->>'ledgerId' AND rollover_status.jsonb->>'toFiscalYearId' = _rollover_record->>'toFiscalYearId' AND
                           (NOT rollover_status.jsonb ? 'overallStatus' OR rollover_status.jsonb->>'overallStatus'='Not Started' OR rollover_status.jsonb->>'overallStatus'='In Progress')
                            AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id;
        ELSEIF
            -- #8
            (_rollover_record->>'restrictEncumbrance')::boolean AND EXISTS (SELECT sum((tr.jsonb->>'amount')::decimal) FROM ${myuniversity}_${mymodule}.transaction tr
                LEFT JOIN ${myuniversity}_${mymodule}.budget budget ON tr.fromFundId=budget.fundId
                WHERE tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id AND tr.fiscalYearId::text=_rollover_record->>'fromFiscalYearId' AND budget.fiscalYearId::text=_rollover_record->>'toFiscalYearId'
                GROUP BY budget.jsonb, tr.jsonb->>'fromFundId'
                HAVING sum(public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record)) > (budget.jsonb->>'available')::decimal +
                                                                                                      ((budget.jsonb->>'allocated')::decimal +
                                                                                                      (budget.jsonb->>'netTransfers')::decimal) *
                                                                                                      (budget.jsonb->>'allowableEncumbrance')::decimal/100 -
                                                                                                      (budget.jsonb->>'encumbered')::decimal -
                                                                                                      (budget.jsonb->>'overEncumbrance')::decimal)
        THEN
            -- #9
            INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_errors (id, jsonb)
                SELECT public.uuid_generate_v4(), jsonb_build_object
                (
                    'ledgerId', _rollover_record->>'ledgerId',
                    'toFiscalYearId', _rollover_record->>'toFiscalYearId',
                    'errorType', 'ORDER',
                    'failedAction', 'Create encumbrance',
                    'errorMessage', 'Insufficient funds',
                    'details', jsonb_build_object
                    (
                        'purchaseOrderId', _order_id,
                        'poLineId', tr.jsonb->'encumbrance'->>'sourcePoLineId',
                        'amount', public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record),
                        'fundId', tr.jsonb->>'fromFundId'						
                    )
                )
                FROM ${myuniversity}_${mymodule}.transaction tr
                INNER JOIN
                    (
                        SELECT budget.jsonb  AS budget
                        FROM ${myuniversity}_${mymodule}.transaction tr
                        LEFT JOIN ${myuniversity}_${mymodule}.budget budget ON tr.jsonb->>'fromFundId'=budget.fundId::text 
                        WHERE tr.jsonb->>'fiscalYearId'=_rollover_record->>'fromFiscalYearId'
                            AND budget.fiscalYearId::text=_rollover_record->>'toFiscalYearId'
                        GROUP BY tr.jsonb->>'fromFundId', budget.jsonb
                        HAVING sum(public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record)) > (budget.jsonb->>'available')::decimal +
                                                                                                            ((budget.jsonb->>'allocated')::decimal +
                                                                                                            (budget.jsonb->>'netTransfers')::decimal) *
                                                                                                            (budget.jsonb->>'allowableEncumbrance')::decimal/100 -
                                                                                                            (budget.jsonb->>'encumbered')::decimal -
                                                                                                            (budget.jsonb->>'overEncumbrance')::decimal
                    ) as summary ON summary.budget->>'fundId'=tr.jsonb->>'fromFundId'
                WHERE tr.jsonb->>'transactionType'='Encumbrance' AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id
                AND tr.fiscalYearId::text=_rollover_record->>'fromFiscalYearId';
        ELSE
            -- 10 create encumbrances
            INSERT INTO ${myuniversity}_${mymodule}.transaction (id, jsonb)
                SELECT public.uuid_generate_v4(), jsonb - 'id' || jsonb_build_object
                    (
                        'fiscalYearId', _rollover_record->>'toFiscalYearId',
                        'amount', public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record),
                        'encumbrance', jsonb->'encumbrance' || jsonb_build_object
                            (
                                'initialAmountEncumbered', public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record),
                                'amountAwaitingPayment', 0,
                                'amountExpended', 0
                            ),
                        'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text)
                        
                    )  
                FROM ${myuniversity}_${mymodule}.transaction tr
                    WHERE tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id AND tr.jsonb->>'fiscalYearId'=_rollover_record->>'fromFiscalYearId'
                        AND public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record) <> 0 AND (tr.jsonb->'encumbrance'->>'reEncumber')::boolean
                        AND tr.jsonb->'encumbrance'->>'status'='Unreleased'; -- TODO: Instead of this use tr.jsonb->'encumbrance'->>'orderStatus'='Open' criteria when MODFISTO-194 is completed
        END IF;

        -- #11 update budget amounts
        UPDATE ${myuniversity}_${mymodule}.budget as budget
        SET jsonb = budget.jsonb || jsonb_build_object
            (
                'available', GREATEST((budget.jsonb->>'available')::decimal - subquery.amount, 0),
                'unavailable', LEAST((budget.jsonb->>'unavailable')::decimal + subquery.amount, (budget.jsonb->>'allocated')::decimal + (budget.jsonb->>'netTransfers')::decimal),
                'encumbered', (budget.jsonb->>'encumbered')::decimal + subquery.amount,
                'overEncumbrance', (budget.jsonb->>'overEncumbrance')::decimal + GREATEST(subquery.amount - (budget.jsonb->>'available')::decimal, 0)
            )
        FROM 
            (
                SELECT tr.jsonb->>'fromFundId' as fund_id, sum((tr.jsonb->>'amount')::decimal) AS amount
                FROM ${myuniversity}_${mymodule}.transaction tr
                WHERE tr.jsonb->>'fiscalYearId'=_rollover_record->>'toFiscalYearId' AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id
                GROUP BY tr.jsonb->>'fromFundId'
            ) AS subquery
        LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON subquery.fund_id=fund.id::text
        WHERE subquery.fund_id=budget.jsonb->>'fundId' AND fund.jsonb->>'ledgerId'=_rollover_record->>'ledgerId' AND budget.jsonb->>'fiscalYearId'=_rollover_record->>'toFiscalYearId';
    END;

$$ LANGUAGE plpgsql;

-- build budget based on corresponding budgetRollover item
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.build_budget(_budget jsonb, _fund jsonb, _rollover_record jsonb, _fiscal_year jsonb) RETURNS jsonb AS $$
    DECLARE
        budget_rollover                 jsonb;
        allocated                       decimal;
        available                       decimal;
        metadata                        jsonb;

    BEGIN
         SELECT br INTO budget_rollover FROM jsonb_array_elements(_rollover_record->'budgetsRollover') br
                     WHERE br->>'fundTypeId'=_fund->>'fundTypeId' OR (NOT br ? 'fundTypeId' AND NOT _fund ? 'fundTypeId');

         IF
            (budget_rollover->>'rolloverAllocation')::boolean
         THEN
            allocated := (_budget->>'allocated')::decimal;
         ELSE
            allocated := 0;
         END IF;

         IF
            (budget_rollover->>'rolloverAvailable')::boolean
         THEN
            available := (_budget->>'available')::decimal;
         ELSE
            available := 0;
         END IF;

        allocated := allocated +
            CASE
                WHEN budget_rollover ? 'adjustAllocation' AND (budget_rollover->>'rolloverAllocation')::boolean
                THEN (_budget->>'allocated')::decimal*(budget_rollover->>'adjustAllocation')::decimal/100
                ELSE 0
            END;

        IF
            budget_rollover->>'addAvailableTo'='Allocation'
        THEN
             allocated := allocated + available;
             available := 0;
        END IF;


        metadata := _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text);

        RETURN (_budget - 'id') || jsonb_build_object
            (
                'fiscalYearId', _rollover_record->>'toFiscalYearId',
                'name', (_fund->>'code') || '-' ||  (_fiscal_year->>'code'),
                'allocated', allocated, -- TODO: After MODFISTO-186 allocated will become readOnly. initialAllocation, allocationTo, allocationFrom will have to be populated
                'available', allocated + available,
                'metadata', metadata,
                'budgetStatus', 'Active',
                'allowableEncumbrance', budget_rollover->>'allowableEncumbrance',
                'allowableExpenditure', budget_rollover->>'allowableExpenditure',
                'unavailable', 0,
                'netTransfers', available,
                'awaitingPayment', 0,
                'encumbered', 0,
                'expenditures', 0,
                'overEncumbrance', 0,
                'overExpended', 0
            );
    END;
$$ LANGUAGE plpgsql;

-- TODO: since we plan to keep the rollover history, it will not be enough just an ledgerId to find the right  rollover record.
--       Options: 1 add additional fiscalYearId parameter, 2 use rollover id instead of ledger id, 3 pass whole rollover record as parameter
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.budget_encumbrances_rollover(_ledger_id TEXT) RETURNS VOID as $$
    DECLARE
            toFiscalYear					jsonb;
            fromFiscalYear					jsonb;
            rollover_record                 jsonb;
            temprow 						record;
    BEGIN

         -- #1
        SELECT INTO rollover_record (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.rollover
                        WHERE _ledger_id = ${myuniversity}_${mymodule}.rollover.jsonb->>'ledgerId';
        SELECT INTO toFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE rollover_record->>'toFiscalYearId'=jsonb->>'id';
        SELECT INTO fromFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE rollover_record->>'fromFiscalYearId'=jsonb->>'id';

        -- #2 Upsert budgets
        INSERT INTO ${myuniversity}_${mymodule}.budget
            (
                SELECT public.uuid_generate_v4(), ${myuniversity}_${mymodule}.build_budget(budget.jsonb, fund.jsonb, rollover_record, toFiscalYear)
                FROM ${myuniversity}_${mymodule}.budget AS budget
                INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id=budget.fundId
                WHERE budget.jsonb->>'fiscalYearId'=rollover_record->>'fromFiscalYearId' AND fund.jsonb->>'ledgerId'=_ledger_id
            )
            ON CONFLICT (lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fundId'::text)), lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fiscalYearId'::text)))
                 DO UPDATE SET jsonb=${myuniversity}_${mymodule}.budget.jsonb || jsonb_build_object
                    (
                        'allocated', (${myuniversity}_${mymodule}.budget.jsonb->>'allocated')::decimal + (EXCLUDED.jsonb->>'allocated')::decimal,
                        'available', (${myuniversity}_${mymodule}.budget.jsonb->>'available')::decimal + (EXCLUDED.jsonb->>'available')::decimal, -- TODO: After MODFISTO-186 allocated will become readOnly. initialAllocation, allocationTo, allocationFrom will have to be updated
                        'netTransfers', (${myuniversity}_${mymodule}.budget.jsonb->>'netTransfers')::decimal + (EXCLUDED.jsonb->>'netTransfers')::decimal,
                        'metadata', ${myuniversity}_${mymodule}.budget.jsonb->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text));

         -- #3 Create allocations
        INSERT INTO ${myuniversity}_${mymodule}.transaction
             (
                SELECT public.uuid_generate_v4(), jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', rollover_record->>'toFiscalYearId', 'transactionType', 'Allocation',
                                                              'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'allocated')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                                                              'metadata', rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                FROM ${myuniversity}_${mymodule}.budget AS budget
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Allocation'
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Allocation'
                WHERE budget.jsonb->>'fiscalYearId'=rollover_record->>'toFiscalYearId'
                GROUP BY budget.jsonb
                HAVING (budget.jsonb->>'allocated')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
             );

        -- #4 Create transfers
        INSERT INTO ${myuniversity}_${mymodule}.transaction
              (
                 SELECT public.uuid_generate_v4(), jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', rollover_record->>'toFiscalYearId', 'transactionType', 'Transfer',
                                                               'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                                                               'metadata', rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                 FROM ${myuniversity}_${mymodule}.budget AS budget
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Transfer'
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Transfer'
                 WHERE budget.jsonb->>'fiscalYearId'=rollover_record->>'toFiscalYearId'
                 GROUP BY budget.jsonb
                 HAVING (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
              );

        -- #5 sort order ids
        FOR temprow IN
            SELECT min(tr.jsonb->'metadata'->>'createdDate') date, tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId' order_id FROM ${myuniversity}_${mymodule}.transaction tr
                LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                LEFT JOIN ${myuniversity}_${mymodule}.ledger ledger ON ledger.id=fund.ledgerId
                WHERE tr.jsonb->>'transactionType' = 'Encumbrance'
                    AND tr.fiscalYearId::text = rollover_record->>'fromFiscalYearId'
                    AND ledger.id::text=_ledger_id
                GROUP BY order_id
                ORDER BY date
        LOOP
            PERFORM ${myuniversity}_${mymodule}.rollover_order(temprow.order_id::text, rollover_record);
        END LOOP;

    END;
$$ LANGUAGE plpgsql;