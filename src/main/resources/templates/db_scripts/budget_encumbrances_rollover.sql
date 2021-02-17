/*
    Entry point - budget_encumbrances_rollover(_rollover_record jsonb) function
    #1 Create budgets using build_budget() function for the toFiscalYearId and every fund related to the ledger,
        if corresponding budget has already been created (ON CONFLICT) then update existed budget with new allocated, available, netTransfers, metadata values
    #1.1 Create budget expense class relations for new budgets
    #1.2 Create budget groups relation for new budgets
    #2  Create allocation for every difference between budget.allocated and sum of corresponding allocations amount
    #3  Create transfer for every difference between budget.netTransfers and sum of corresponding transfers amount
    #4  Call rollover_order(_order_id text, _rollover_record jsonb) function for every order id ordered by the lowest creation date of related encumbrances
    For every order id
    #5 Check if there is any encumbrance that need to be rollovered for ledger related to order for which the rollover has not been completed yet
    #6 If #5 is true than create rollover error record
    #7 if #5 is false then check if rollover restrictEncumbrance setting is true and there is any sum of expected encumbrances grouped by fromFundId greater than corresponding budget remaining amount
    #8 If #7 is true then create corresponding rollover error
    #9 if #7 is false create encumbrances with amount non-zero amount calculated with calculate_planned_encumbrance_amount(_transaction jsonb, _rollover_record jsonb) function
    #10 update budget available, unavailable, encumbered, overEncumbrance by sum of encumbrances amount created on #10 step
    #11 Check budget existence
    #12 If #11 is true create corresponding rollover error
 */
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
-- Map encumbrance with corresponding encumbranceRollover item, calculate expected encumbrance amount based on that item
CREATE OR REPLACE FUNCTION public.calculate_planned_encumbrance_amount(_transaction jsonb, _rollover_record jsonb) RETURNS decimal as $$
	DECLARE
		amount DECIMAL DEFAULT 0;
		encumbrance_rollover jsonb DEFAULT null;
		po_line_cost DECIMAL DEFAULT 0;
		total_amount DECIMAL DEFAULT 0;
		distribution_value DECIMAL DEFAULT 0;
	BEGIN

	    SELECT sum((jsonb->'encumbrance'->>'initialAmountEncumbered')::decimal) INTO po_line_cost
	        FROM ${myuniversity}_${mymodule}.transaction
            WHERE _rollover_record->>'fromFiscalYearId'=jsonb->>'fiscalYearId' AND jsonb->'encumbrance'->>'sourcePoLineId'=_transaction->'encumbrance'->>'sourcePoLineId'
            GROUP BY jsonb->'encumbrance'->>'sourcePoLineId';

        distribution_value := (_transaction->'encumbrance'->>'initialAmountEncumbered')::decimal/po_line_cost;

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
		    SELECT sum((jsonb->'encumbrance'->>'amountExpended')::decimal) INTO total_amount
            	        FROM ${myuniversity}_${mymodule}.transaction
                        WHERE _rollover_record->>'fromFiscalYearId'=jsonb->>'fiscalYearId' AND jsonb->'encumbrance'->>'sourcePoLineId'=_transaction->'encumbrance'->>'sourcePoLineId'
                        GROUP BY jsonb->'encumbrance'->>'sourcePoLineId';

		ELSE
			SELECT sum((jsonb->>'amount')::decimal) INTO total_amount
                        	        FROM ${myuniversity}_${mymodule}.transaction
                                    WHERE _rollover_record->>'fromFiscalYearId'=jsonb->>'fiscalYearId' AND jsonb->'encumbrance'->>'sourcePoLineId'=_transaction->'encumbrance'->>'sourcePoLineId'
                                    GROUP BY jsonb->'encumbrance'->>'sourcePoLineId';
		END IF;
		total_amount:= total_amount + total_amount * (encumbrance_rollover->>'increaseBy')::decimal/100;
		amount := total_amount * distribution_value;
		RETURN amount;
	END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.rollover_order(_order_id text, _rollover_record jsonb) RETURNS VOID as $$

    BEGIN
        IF
            -- #5
            EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.transaction tr
                  LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                  LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover rollover ON rollover.ledgerId = fund.ledgerId
                  LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_progress rollover_progress ON rollover.id = rollover_progress.ledgerRolloverId
                  WHERE fund.ledgerId::text<>_rollover_record->>'ledgerId' AND tr.fiscalYearId::text = _rollover_record->>'fromFiscalYearId' AND
                           (rollover_progress.jsonb IS NULL OR rollover_progress.jsonb->>'overallRolloverStatus'='Not Started' OR rollover_progress.jsonb->>'overallRolloverStatus'='In Progress')
                            AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id)
        THEN
            -- #6
            INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_error (id, jsonb)
                SELECT public.uuid_generate_v4(), jsonb_build_object
                (
                    'ledgerRolloverId', _rollover_record->>'id',
                    'errorType', 'Order',
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
                    WHERE fund.ledgerId::text=_rollover_record->>'ledgerId' AND tr.fiscalYearId::text = _rollover_record->>'fromFiscalYearId'
                          AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id;
        ELSEIF
           -- #10
           EXISTS (SELECT tr.jsonb as transaction FROM ${myuniversity}_${mymodule}.transaction tr
           					 WHERE NOT EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.budget budget
           								 	WHERE tr.fromFundId=budget.fundId AND budget.fiscalYearId::text = _rollover_record->>'toFiscalYearId')
           						AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'= _order_id
                               	AND tr.fiscalYearId::text= _rollover_record->>'fromFiscalYearId')
       THEN
           -- #11
           INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_error (id, jsonb)
               SELECT public.uuid_generate_v4(), jsonb_build_object
               (
                   'ledgerRolloverId', _rollover_record->>'id',
                   'errorType', 'Order',
                   'failedAction', 'Create encumbrance',
                   'errorMessage', 'Budget not found',
                   'details', jsonb_build_object
                   (
                       'purchaseOrderId', _order_id,
                       'poLineId', tr.jsonb->'encumbrance'->>'sourcePoLineId',
                       'amount', public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record),
                       'fundId', tr.jsonb->>'fromFundId'
                   )
               )
               FROM ${myuniversity}_${mymodule}.transaction tr
                 WHERE NOT EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.budget budget
                                WHERE tr.fromFundId=budget.fundId AND budget.fiscalYearId::text = _rollover_record->>'toFiscalYearId')
                    AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'= _order_id
                    AND tr.fiscalYearId::text= _rollover_record->>'fromFiscalYearId';
        ELSEIF
            -- #7
            (_rollover_record->>'restrictEncumbrance')::boolean AND EXISTS (SELECT sum((tr.jsonb->>'amount')::decimal) FROM ${myuniversity}_${mymodule}.transaction tr
                LEFT JOIN ${myuniversity}_${mymodule}.budget budget ON tr.fromFundId=budget.fundId
                WHERE budget.jsonb->>'allowableEncumbrance' IS NOT NULL AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id AND tr.fiscalYearId::text=_rollover_record->>'fromFiscalYearId' AND budget.fiscalYearId::text=_rollover_record->>'toFiscalYearId'
                GROUP BY budget.jsonb, tr.jsonb->>'fromFundId'
                HAVING sum(public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record)) > ((budget.jsonb->>'initialAllocation')::decimal +
                                                                                                      (budget.jsonb->>'allocationTo')::decimal -
                                                                                                      (budget.jsonb->>'allocationFrom')::decimal +
                                                                                                      (budget.jsonb->>'netTransfers')::decimal) *
                                                                                                      (budget.jsonb->>'allowableEncumbrance')::decimal/100 -
                                                                                                      (budget.jsonb->>'encumbered')::decimal)
        THEN
            -- #8
            INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_error (id, jsonb)
                SELECT public.uuid_generate_v4(), jsonb_build_object
                (
                    'ledgerRolloverId', _rollover_record->>'id',
                    'errorType', 'Order',
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
                        WHERE budget.jsonb->>'allowableEncumbrance' IS NOT NULL
                            AND tr.jsonb->>'fiscalYearId'=_rollover_record->>'fromFiscalYearId'
                            AND budget.fiscalYearId::text=_rollover_record->>'toFiscalYearId'
                        GROUP BY tr.jsonb->>'fromFundId', budget.jsonb
                        HAVING sum(public.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record)) > ((budget.jsonb->>'initialAllocation')::decimal +
                                                                                                            (budget.jsonb->>'allocationTo')::decimal -
                                                                                                            (budget.jsonb->>'allocationFrom')::decimal +
                                                                                                            (budget.jsonb->>'netTransfers')::decimal) *
                                                                                                            (budget.jsonb->>'allowableEncumbrance')::decimal/100 -
                                                                                                            (budget.jsonb->>'encumbered')::decimal
                    ) as summary ON summary.budget->>'fundId'=tr.jsonb->>'fromFundId'
                WHERE tr.jsonb->>'transactionType'='Encumbrance' AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id
                AND tr.fiscalYearId::text=_rollover_record->>'fromFiscalYearId';
        ELSE
            -- #9 create encumbrances
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
                        AND tr.jsonb->'encumbrance'->>'orderStatus'='Open';
        END IF;

        -- #10 update budget amounts
        UPDATE ${myuniversity}_${mymodule}.budget as budget
        SET jsonb = budget.jsonb || jsonb_build_object('encumbered', (budget.jsonb->>'encumbered')::decimal + subquery.amount)
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
        newAllocated                    decimal;
        newNetTransfers                 decimal;
        allocated                       decimal;
        totalFunding                    decimal;
        available                       decimal;
        unavailable                     decimal;
        allowableEncumbrance            decimal;
        allowableExpenditure            decimal;
        metadata                        jsonb;
        result_budget                   jsonb;

    BEGIN
         SELECT br INTO budget_rollover FROM jsonb_array_elements(_rollover_record->'budgetsRollover') br
                     WHERE br->>'fundTypeId'=_fund->>'fundTypeId' OR (NOT br ? 'fundTypeId' AND NOT _fund ? 'fundTypeId');

         allocated := (_budget->>'initialAllocation')::decimal + (_budget->>'allocationTo')::decimal - (_budget->>'allocationFrom')::decimal;
         totalFunding := allocated + (_budget->>'netTransfers')::decimal;
         unavailable := (_budget->>'encumbered')::decimal + (_budget->>'expenditures')::decimal + (_budget->>'awaitingPayment')::decimal;
         available := totalFunding - unavailable;

         IF
            (budget_rollover->>'rolloverAllocation')::boolean
         THEN
            newAllocated := allocated;
         ELSE
            newAllocated := 0;
         END IF;

         IF
            (budget_rollover->>'rolloverAvailable')::boolean
         THEN
            newNetTransfers := available;
         ELSE
            newNetTransfers := 0;
         END IF;

         IF
             (budget_rollover->>'setAllowances')::boolean
         THEN
             allowableEncumbrance := budget_rollover->>'allowableEncumbrance';
             allowableExpenditure := budget_rollover->>'allowableExpenditure';
         ELSE
             allowableEncumbrance := _budget->>'allowableEncumbrance';
             allowableExpenditure := _budget->>'allowableExpenditure';
         END IF;

        newAllocated := newAllocated +
            CASE
                WHEN budget_rollover ? 'adjustAllocation' AND (budget_rollover->>'rolloverAllocation')::boolean
                THEN allocated*(budget_rollover->>'adjustAllocation')::decimal/100
                ELSE 0
            END;

        IF
            budget_rollover->>'addAvailableTo'='Allocation'
        THEN
             newAllocated := newAllocated + newNetTransfers;
             newNetTransfers := 0;
        END IF;


        metadata := _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text);

        result_budget := (_budget - 'id' - 'allowableEncumbrance' - 'allowableExpenditure') || jsonb_build_object
            (
                'fiscalYearId', _rollover_record->>'toFiscalYearId',
                'name', (_fund->>'code') || '-' ||  (_fiscal_year->>'code'),
                'initialAllocation', newAllocated,
                'allocationTo', 0,
                'allocationFrom', 0,
                'metadata', metadata,
                'budgetStatus', 'Active',
                'netTransfers', newNetTransfers,
                'awaitingPayment', 0,
                'encumbered', 0,
                'expenditures', 0
            );

        IF allowableEncumbrance is not null
        THEN
            result_budget := result_budget || jsonb_build_object('allowableEncumbrance', allowableEncumbrance);
        END IF;

        IF allowableExpenditure is not null
        THEN
            result_budget := result_budget || jsonb_build_object('allowableExpenditure', allowableExpenditure);
        END IF;

        RETURN result_budget;
    END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.budget_encumbrances_rollover(_rollover_record jsonb) RETURNS VOID as $$
    DECLARE
            toFiscalYear					jsonb;
            fromFiscalYear					jsonb;
            temprow 						record;
    BEGIN


        SELECT INTO toFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE _rollover_record->>'toFiscalYearId'=jsonb->>'id';
        SELECT INTO fromFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE _rollover_record->>'fromFiscalYearId'=jsonb->>'id';

        -- #1 Upsert budgets
        INSERT INTO ${myuniversity}_${mymodule}.budget
            (
                SELECT public.uuid_generate_v4(), ${myuniversity}_${mymodule}.build_budget(budget.jsonb, fund.jsonb, _rollover_record, toFiscalYear)
                FROM ${myuniversity}_${mymodule}.budget AS budget
                INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id=budget.fundId
                WHERE fund.jsonb->>'fundStatus'<>'Inactive' AND budget.jsonb->>'fiscalYearId'=_rollover_record->>'fromFiscalYearId' AND fund.jsonb->>'ledgerId'=_rollover_record->>'ledgerId'
            )
            ON CONFLICT (lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fundId'::text)), lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fiscalYearId'::text)))
                 DO UPDATE SET jsonb=${myuniversity}_${mymodule}.budget.jsonb || jsonb_build_object
                    (
                        'allocationTo', (${myuniversity}_${mymodule}.budget.jsonb->>'allocationTo')::decimal + (EXCLUDED.jsonb->>'initialAllocation')::decimal,
                        'netTransfers', (${myuniversity}_${mymodule}.budget.jsonb->>'netTransfers')::decimal + (EXCLUDED.jsonb->>'netTransfers')::decimal,
                        'metadata', ${myuniversity}_${mymodule}.budget.jsonb->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text));

        -- #1.1 Create budget expense class relations for new budgets
        INSERT INTO ${myuniversity}_${mymodule}.budget_expense_class
        SELECT public.uuid_generate_v4(),
               jsonb_build_object('budgetId', newBudget.id,
                                  'expenseClassId', exp.jsonb->>'expenseClassId',
                                  'status', exp.jsonb->>'status')
        FROM ${myuniversity}_${mymodule}.budget AS oldBudget
                 INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.budget AS newBudget ON newBudget.fundId = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.budget_expense_class AS exp ON oldBudget.id = exp.budgetid
        WHERE oldBudget.jsonb ->> 'fiscalYearId' = _rollover_record->>'fromFiscalYearId'
          AND fund.jsonb ->> 'ledgerId' = _rollover_record->>'ledgerId'
          AND newBudget.jsonb->>'fiscalYearId' = _rollover_record->>'toFiscalYearId'
        ON CONFLICT DO NOTHING;

        -- #1.2 Create budget groups relation for new budgets
        INSERT INTO ${myuniversity}_${mymodule}.group_fund_fiscal_year
        SELECT public.uuid_generate_v4(),
               jsonb_build_object('budgetId', newBudget.id,
                                  'groupId', gr.jsonb->>'groupId',
                                  'fiscalYearId', _rollover_record->>'toFiscalYearId',
                                  'fundId', gr.jsonb->>'fundId')
        FROM ${myuniversity}_${mymodule}.budget AS oldBudget
                 INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.budget AS newBudget ON newBudget.fundId = oldBudget.fundId
                 INNER JOIN ${myuniversity}_${mymodule}.group_fund_fiscal_year AS gr ON oldBudget.id = gr.budgetid
        WHERE oldBudget.jsonb ->> 'fiscalYearId' = _rollover_record->>'fromFiscalYearId'
          AND fund.jsonb ->> 'ledgerId' = _rollover_record->>'ledgerId'
          AND newBudget.jsonb->>'fiscalYearId' = _rollover_record->>'toFiscalYearId'
        ON CONFLICT DO NOTHING;

         -- #2 Create allocations
        INSERT INTO ${myuniversity}_${mymodule}.transaction
             (
                SELECT public.uuid_generate_v4(), jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', _rollover_record->>'toFiscalYearId', 'transactionType', 'Allocation',
                                                              'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'initialAllocation')::decimal+
                                                                                                                                (budget.jsonb->>'allocationTo')::decimal-
                                                                                                                                (budget.jsonb->>'allocationFrom')::decimal-
                                                                                                                                sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                                                              'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                FROM ${myuniversity}_${mymodule}.budget AS budget
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Allocation'
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Allocation'
                WHERE budget.jsonb->>'fiscalYearId'=_rollover_record->>'toFiscalYearId'
                GROUP BY budget.jsonb
                HAVING (budget.jsonb->>'initialAllocation')::decimal+(budget.jsonb->>'allocationTo')::decimal-(budget.jsonb->>'allocationFrom')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
             );

        -- #3 Create transfers
        INSERT INTO ${myuniversity}_${mymodule}.transaction
              (
                 SELECT public.uuid_generate_v4(), jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', _rollover_record->>'toFiscalYearId', 'transactionType', 'Rollover transfer',
                                                               'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                                                               'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                 FROM ${myuniversity}_${mymodule}.budget AS budget
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Transfer'
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Transfer'
                 WHERE budget.jsonb->>'fiscalYearId'=_rollover_record->>'toFiscalYearId'
                 GROUP BY budget.jsonb
                 HAVING (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
              );

        -- #4 sort order ids
        FOR temprow IN
            SELECT min(tr.jsonb->'metadata'->>'createdDate') date, tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId' order_id FROM ${myuniversity}_${mymodule}.transaction tr
                LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                LEFT JOIN ${myuniversity}_${mymodule}.ledger ledger ON ledger.id=fund.ledgerId
                WHERE tr.jsonb->>'transactionType' = 'Encumbrance'
                    AND tr.fiscalYearId::text = _rollover_record->>'fromFiscalYearId'
                    AND tr.jsonb->'encumbrance'->>'orderStatus' = 'Open'
                    AND (tr.jsonb->'encumbrance'->>'reEncumber')::boolean
                    AND ledger.id::text=_rollover_record->>'ledgerId'
                GROUP BY order_id
                ORDER BY date
        LOOP
            PERFORM ${myuniversity}_${mymodule}.rollover_order(temprow.order_id::text, _rollover_record);
        END LOOP;

    END;
$$ LANGUAGE plpgsql;
