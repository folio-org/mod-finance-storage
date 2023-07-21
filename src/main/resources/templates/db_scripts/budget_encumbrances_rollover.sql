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
      #6.1 If #5 is true and rolloverType != Preview then stop processing for order. If rolloverType = Preview then proceed with validations and result building taking into account only requested ledgerId
      #7 if #5 is false or #5 is true and rolloverType = Preview then check if rollover restrictEncumbrance setting is true and there is any sum of expected encumbrances grouped by fromFundId greater than corresponding budget remaining amount
      #8 If #7 is true then create corresponding rollover error
      #9 if #7 is false create temp encumbrances with amount non-zero amount calculated with calculate_planned_encumbrance_amount(_transaction jsonb, _rollover_record jsonb) function
      #9.1 Calculate and add missing penny to appropriate temp transaction
      #9.2 move transactions from temp table to permanent
      #10 update budget available, unavailable, encumbered, overEncumbrance by sum of encumbrances amount created on #10 step
      #11 Check budget existence
      #12 If #11 is true create corresponding rollover error
    Finish rollover_order function
    #13 update planned budget status to active

    NOTE: uuid_generate_v4() cannot be used to generate uuids because of pgpool2. uuid_generate_v5() is used instead, with unique strings.
 */
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
-- Map encumbrance with corresponding encumbranceRollover item, calculate expected encumbrance amount based on that item
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(_transaction jsonb, _rollover_record jsonb, _rounding boolean) RETURNS decimal as $$
	DECLARE
		amount DECIMAL DEFAULT 0;
		encumbrance_rollover jsonb DEFAULT null;
		po_line_cost DECIMAL DEFAULT 0;
		total_amount DECIMAL DEFAULT 0;
		distribution_value DECIMAL DEFAULT 0;
		input_fromFiscalYearId uuid := _rollover_record->>'fromFiscalYearId';
	BEGIN

	    SELECT sum((jsonb->'encumbrance'->>'initialAmountEncumbered')::decimal) INTO po_line_cost
	        FROM ${myuniversity}_${mymodule}.transaction
            WHERE input_fromFiscalYearId=fiscalYearId AND jsonb->'encumbrance'->>'sourcePoLineId'=_transaction->'encumbrance'->>'sourcePoLineId'
            GROUP BY jsonb->'encumbrance'->>'sourcePoLineId';

    distribution_value := 0;

		IF
			po_line_cost > 0
		THEN
			distribution_value := (_transaction->'encumbrance'->>'initialAmountEncumbered')::decimal/po_line_cost;
	 	END IF;

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
      encumbrance_rollover IS NULL
    THEN
      RETURN 0.0;
    END IF;

		IF
			encumbrance_rollover->>'basedOn'='Expended'
		THEN
		    SELECT sum((jsonb->'encumbrance'->>'amountExpended')::decimal) INTO total_amount
            	        FROM ${myuniversity}_${mymodule}.transaction
                        WHERE input_fromFiscalYearId=fiscalYearId AND jsonb->'encumbrance'->>'sourcePoLineId'=_transaction->'encumbrance'->>'sourcePoLineId'
                        GROUP BY jsonb->'encumbrance'->>'sourcePoLineId';

		ELSEIF
		  encumbrance_rollover->>'basedOn'='Remaining'
		THEN
			SELECT sum((jsonb->>'amount')::decimal) INTO total_amount
                        	        FROM ${myuniversity}_${mymodule}.transaction
                                    WHERE input_fromFiscalYearId=fiscalYearId AND jsonb->'encumbrance'->>'sourcePoLineId'=_transaction->'encumbrance'->>'sourcePoLineId'
                                    GROUP BY jsonb->'encumbrance'->>'sourcePoLineId';

    ELSE
      SELECT sum((jsonb->'encumbrance'->>'initialAmountEncumbered')::decimal) INTO total_amount
                      FROM ${myuniversity}_${mymodule}.transaction
                        WHERE input_fromFiscalYearId=fiscalYearId AND jsonb->'encumbrance'->>'sourcePoLineId'=_transaction->'encumbrance'->>'sourcePoLineId'
                        GROUP BY jsonb->'encumbrance'->>'sourcePoLineId';

		END IF;
		total_amount:= total_amount + total_amount * (encumbrance_rollover->>'increaseBy')::decimal/100;
		amount := total_amount * distribution_value;
	    IF
	        _rounding IS NOT NULL AND _rounding
	    THEN
		    RETURN ROUND(amount,(_rollover_record->>'currencyFactor')::integer);
	    ELSE
            RETURN amount;
        END IF;
	END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.rollover_order(_order_id text, _rollover_record jsonb) RETURNS VOID as $$
    DECLARE
        missing_penny_with_po_line refcursor;
        missing_penny_row record;
        missing_penny_transaction_id text;
        update_budget_amounts_query text;
        related_not_rollovered_ledger_ids uuid[];
        related_not_rollovered_ledger_descriptions text[];
        input_fromFiscalYearId uuid := _rollover_record->>'fromFiscalYearId';
        input_toFiscalYearId uuid := _rollover_record->>'toFiscalYearId';
        input_ledgerId uuid := _rollover_record->>'ledgerId';
    BEGIN

        -- #9 create encumbrances to temp table
        CREATE TEMPORARY TABLE tmp_transaction(LIKE ${myuniversity}_${mymodule}.transaction);

        INSERT INTO tmp_transaction(id, jsonb)
        SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER1', tr.id)), tr.jsonb - 'id' || jsonb_build_object
            (
                'fiscalYearId', input_toFiscalYearId,
                'amount', ${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record, true),
                'encumbrance', tr.jsonb->'encumbrance' || jsonb_build_object
                    (
                        'initialAmountEncumbered', ${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record, true),
                        'amountAwaitingPayment', 0,
                        'amountExpended', 0,
                        'status', 'Unreleased'
                    ),
                'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', to_char(clock_timestamp(),'YYYY-MM-DD"T"HH24:MI:SS.MSTZHTZM'))

            )
        FROM ${myuniversity}_${mymodule}.transaction tr
        LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
        WHERE tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id AND tr.fiscalYearId=input_fromFiscalYearId
          AND (tr.jsonb->'encumbrance'->>'reEncumber')::boolean AND tr.jsonb->'encumbrance'->>'orderStatus'='Open'
          AND (_rollover_record->>'rolloverType' <> 'Preview' OR (_rollover_record->>'rolloverType' = 'Preview' AND fund.ledgerId = input_ledgerId));

        -- #9.1 calculate and add missing penny to appropriate temp transaction
        -- find poLines and calculate missing penny amount for that poLine if any
        OPEN missing_penny_with_po_line FOR
            SELECT po.id                                                     as po_line_id,
                   (round((SELECT sum(${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(jsonb, _rollover_record, false)) -
                                  sum(${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(jsonb, _rollover_record, true)) penny
                           FROM ${myuniversity}_${mymodule}.transaction
                           WHERE input_fromFiscalYearId = fiscalYearId
                             AND jsonb -> 'encumbrance' ->> 'sourcePoLineId' = po.id
                           GROUP BY jsonb -> 'encumbrance' ->> 'sourcePoLineId'),
                          (_rollover_record ->> 'currencyFactor')::integer)) as penny
            FROM (
                     SELECT DISTINCT tr.jsonb -> 'encumbrance' ->> 'sourcePoLineId' as id
                     FROM ${myuniversity}_${mymodule}.transaction tr
                     WHERE tr.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = _order_id
                       AND input_fromFiscalYearId = fiscalYearId
                 ) po;
        -- if missing penny for poLines exist then find transaction (first or last) and add that missing amount to them
        LOOP
            FETCH missing_penny_with_po_line INTO missing_penny_row;
            EXIT WHEN NOT found;

            IF missing_penny_row.penny IS NOT NULL AND missing_penny_row.penny != 0
            THEN
                missing_penny_transaction_id :=
                        (
                            SELECT id
                            FROM tmp_transaction
                            WHERE input_toFiscalYearId = fiscalYearId
                              AND jsonb -> 'encumbrance' ->> 'sourcePoLineId' = missing_penny_row.po_line_id
                            ORDER BY CASE WHEN missing_penny_row.penny < 0 THEN jsonb -> 'metadata' ->> 'createdDate' END,
                                     CASE WHEN missing_penny_row.penny > 0 THEN jsonb -> 'metadata' ->> 'createdDate' END DESC
                            LIMIT 1
                        );

                IF missing_penny_transaction_id IS NOT NULL
                THEN
                    UPDATE tmp_transaction as tr
                    SET jsonb = jsonb_set(
                                jsonb || jsonb_build_object('amount', (jsonb ->> 'amount')::decimal + missing_penny_row.penny),
                                '{encumbrance,initialAmountEncumbered}',
                                ((jsonb -> 'encumbrance' ->> 'initialAmountEncumbered')::decimal +
                                 missing_penny_row.penny)::text::jsonb)
                    WHERE missing_penny_transaction_id::uuid = id;
                END IF;
            END IF;
        END LOOP;
        CLOSE missing_penny_with_po_line;

        SELECT array_agg(DISTINCT fund.ledgerId) INTO related_not_rollovered_ledger_ids FROM ${myuniversity}_${mymodule}.transaction tr
            LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
            LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover rollover ON rollover.ledgerId = fund.ledgerId AND rollover.jsonb->>'rolloverType'<>'Preview'
                AND rollover.fromfiscalyearid = input_fromFiscalYearId
            LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_progress rollover_progress ON rollover.id = rollover_progress.ledgerRolloverId
            WHERE fund.ledgerId<>input_ledgerId AND tr.fiscalYearId = input_fromFiscalYearId AND
                (rollover_progress.jsonb IS NULL OR rollover_progress.jsonb->>'overallRolloverStatus'='Not Started' OR rollover_progress.jsonb->>'overallRolloverStatus'='In Progress')
                 AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id;

        -- #5
        IF array_length(related_not_rollovered_ledger_ids, 1) > 0
        THEN
            -- #6
            SELECT array_agg(format('%s (id=%s)', jsonb->>'name', id)) INTO related_not_rollovered_ledger_descriptions FROM ${myuniversity}_${mymodule}.ledger
                WHERE id = ANY(related_not_rollovered_ledger_ids);
            INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_error (id, jsonb)
                SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER2', _rollover_record->>'id', tr.id, fund.id)), jsonb_build_object
                (
                    'ledgerRolloverId', _rollover_record->>'id',
                    'errorType', 'Order',
                    'failedAction', 'Create encumbrance',
                    'errorMessage', '[WARNING] Part of the encumbrances belong to the ledger, which has not been rollovered. Ledgers to rollover: ' || array_to_string(related_not_rollovered_ledger_descriptions, ', '),
                    'details', jsonb_build_object
                    (
                        'purchaseOrderId', _order_id,
                        'poLineId', tr.jsonb->'encumbrance'->>'sourcePoLineId',
                        'amount', ${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record, true),
                        'fundId', tr.fromFundId::text
                    )
                )
                FROM ${myuniversity}_${mymodule}.transaction tr
                    LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                    WHERE fund.ledgerId=input_ledgerId AND tr.fiscalYearId = input_fromFiscalYearId
                          AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id;
        END IF;

        -- #6.1 stop order processing for rolloverType != Preview. If rolloverType = Preview then proceed with validations and result building taking into account only requested ledgerId.
        IF _rollover_record->>'rolloverType' <> 'Preview' AND array_length(related_not_rollovered_ledger_ids, 1) > 0
        THEN
        ELSEIF
           -- #10
           EXISTS (SELECT tr.jsonb as transaction FROM ${myuniversity}_${mymodule}.transaction tr
                   LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                   LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover rollover ON rollover.ledgerId = fund.ledgerId
                       AND rollover.jsonb->>'rolloverType'<>'Preview'
           					 WHERE NOT EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_budget budget
           								 	WHERE tr.fromFundId=budget.fundId
           								 	  AND budget.fiscalYearId = input_toFiscalYearId
           								 	  AND (budget.ledgerRolloverId::text = _rollover_record->>'id'
           								 	         OR (fund.ledgerId <> input_ledgerId AND rollover.jsonb IS NOT NULL)))
           						AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'= _order_id
                      AND tr.fiscalYearId= input_fromFiscalYearId
                      AND (_rollover_record->>'rolloverType' <> 'Preview' OR (_rollover_record->>'rolloverType' = 'Preview' AND fund.ledgerId = input_ledgerId)))
        THEN
           -- #11
           INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_error (id, jsonb)
               SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER3', _rollover_record->>'id', tr.id)), jsonb_build_object
               (
                   'ledgerRolloverId', _rollover_record->>'id',
                   'errorType', 'Order',
                   'failedAction', 'Create encumbrance',
                   'errorMessage', 'Budget not found',
                   'details', jsonb_build_object
                   (
                       'purchaseOrderId', _order_id,
                       'poLineId', tr.jsonb->'encumbrance'->>'sourcePoLineId',
                       'amount', ${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record, true),
                       'fundId', tr.jsonb->>'fromFundId'
                   )
               )
               FROM ${myuniversity}_${mymodule}.transaction tr
                 WHERE NOT EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_budget budget
                                WHERE tr.fromFundId=budget.fundId
                                  AND budget.fiscalYearId = input_toFiscalYearId
                                  AND budget.ledgerRolloverId::text = _rollover_record->>'id')
                    AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'= _order_id
                    AND tr.fiscalYearId= input_fromFiscalYearId;
        ELSEIF
            -- #7
            (_rollover_record->>'restrictEncumbrance')::boolean AND EXISTS (SELECT sum((tr.jsonb->>'amount')::decimal) FROM tmp_transaction tr
                LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_budget budget ON tr.fromFundId = budget.fundId
                                                                            WHERE budget.jsonb ->> 'allowableEncumbrance' IS NOT NULL
                                                                              AND tr.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = _order_id
                                                                              AND tr.fiscalYearId = input_toFiscalYearId
                                                                              AND budget.fiscalYearId = input_toFiscalYearId
                                                                              AND budget.ledgerRolloverId = (_rollover_record->>'id')::uuid
                                                                            GROUP BY budget.jsonb, tr.fromFundId
                HAVING sum((tr.jsonb->>'amount')::decimal) > ((budget.jsonb->>'initialAllocation')::decimal +
                                                                                                      (budget.jsonb->>'allocationTo')::decimal -
                                                                                                      (budget.jsonb->>'allocationFrom')::decimal +
                                                                                                      (budget.jsonb->>'netTransfers')::decimal) *
                                                                                                      (budget.jsonb->>'allowableEncumbrance')::decimal/100 -
                                                                                                      (budget.jsonb->>'encumbered')::decimal)
        THEN
            -- #8
            INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_error (id, jsonb)
                SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER4', _rollover_record->>'id', tr.id, summary.budget->>'id')), jsonb_build_object
                (
                    'ledgerRolloverId', _rollover_record->>'id',
                    'errorType', 'Order',
                    'failedAction', 'Create encumbrance',
                    'errorMessage', 'Insufficient funds',
                    'details', jsonb_build_object
                    (
                        'purchaseOrderId', _order_id,
                        'poLineId', tr.jsonb->'encumbrance'->>'sourcePoLineId',
                        'amount', ${myuniversity}_${mymodule}.calculate_planned_encumbrance_amount(tr.jsonb, _rollover_record, true),
                        'fundId', tr.jsonb->>'fromFundId'
                    )
                )
                FROM ${myuniversity}_${mymodule}.transaction tr
                INNER JOIN
                    (
                        SELECT budget.jsonb  AS budget
                        FROM tmp_transaction tr
                        LEFT JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_budget budget ON tr.fromFundId=budget.fundId
                        WHERE budget.jsonb->>'allowableEncumbrance' IS NOT NULL
                            AND tr.fiscalYearId=input_toFiscalYearId
                            AND budget.fiscalYearId=input_toFiscalYearId
                            AND budget.ledgerRolloverId = (_rollover_record->>'id')::uuid
                        GROUP BY tr.fromFundId, budget.jsonb
                        HAVING sum((tr.jsonb->>'amount')::decimal) > ((budget.jsonb->>'initialAllocation')::decimal +
                                                                                                            (budget.jsonb->>'allocationTo')::decimal -
                                                                                                            (budget.jsonb->>'allocationFrom')::decimal +
                                                                                                            (budget.jsonb->>'netTransfers')::decimal) *
                                                                                                            (budget.jsonb->>'allowableEncumbrance')::decimal/100 -
                                                                                                            (budget.jsonb->>'encumbered')::decimal
                    ) as summary ON (summary.budget->>'fundId')::uuid=tr.fromFundId
                WHERE tr.jsonb->>'transactionType'='Encumbrance' AND tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId'=_order_id
                AND tr.fiscalYearId=input_fromFiscalYearId;
        ELSE
            -- #9.2 move transactions from temp table to permanent
            IF _rollover_record->>'rolloverType' = 'Preview' THEN
                -- tmp_transaction table always contains only one record, because function rollover_order() invokes for each order
                -- tmp_encumbered_transactions contains data for all orders with updated encumbered field
                INSERT INTO tmp_encumbered_transactions SELECT * FROM tmp_transaction;
            ELSE
                INSERT INTO ${myuniversity}_${mymodule}.transaction SELECT * FROM tmp_transaction;
            END IF;
        END IF;

        -- #10 update budget encumbered amount,
        -- fields available, unavailable, overEncumbrance also will be updated based on encumbered amount later in java code by CalculationUtils class
        update_budget_amounts_query := '
            UPDATE ${myuniversity}_${mymodule}.%1$I as budget
            SET jsonb = budget.jsonb || jsonb_build_object(''encumbered'', (budget.jsonb->>''encumbered'')::decimal + subquery.amount)
            FROM
                (
                    SELECT (jsonb->>''fromFundId'')::uuid AS fund_id, sum((jsonb->>''amount'')::decimal) AS amount FROM (
                        SELECT jsonb FROM ${myuniversity}_${mymodule}.transaction
                        UNION
                        SELECT jsonb FROM tmp_encumbered_transactions
                    ) AS jsonb
                WHERE jsonb->>''fiscalYearId''=%2$L::jsonb->>''toFiscalYearId'' AND jsonb->''encumbrance''->>''sourcePurchaseOrderId''=%3$L
                GROUP BY fund_id
            ) AS subquery
            LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON subquery.fund_id=fund.id
            WHERE subquery.fund_id=budget.fundId AND fund.ledgerId=(%2$L::jsonb->>''ledgerId'')::uuid AND budget.fiscalYearId=(%2$L::jsonb->>''toFiscalYearId'')::uuid
                AND (NOT budget.jsonb ? ''ledgerRolloverId'' OR budget.jsonb->>''ledgerRolloverId''=%2$L::jsonb->>''id'');';

        IF _rollover_record->>'rolloverType' = 'Preview' THEN
            EXECUTE format(update_budget_amounts_query, 'ledger_fiscal_year_rollover_budget', _rollover_record, _order_id);
        ELSE
            EXECUTE format(update_budget_amounts_query, 'budget', _rollover_record, _order_id);
            EXECUTE format(update_budget_amounts_query, 'ledger_fiscal_year_rollover_budget', _rollover_record, _order_id);

            INSERT INTO tmp_encumbered_transactions SELECT * FROM tmp_transaction
            ON CONFLICT DO NOTHING;
        END IF;

        DROP TABLE IF EXISTS tmp_transaction;
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
        expended                        decimal;
        cashBalance                     decimal;
        allowableEncumbrance            decimal;
        allowableExpenditure            decimal;
        metadata                        jsonb;
        result_budget                   jsonb;

    BEGIN
         SELECT br INTO budget_rollover FROM jsonb_array_elements(_rollover_record->'budgetsRollover') br
                     WHERE br->>'fundTypeId'=_fund->>'fundTypeId' OR (NOT br ? 'fundTypeId' AND NOT _fund ? 'fundTypeId');

         expended := (_budget->>'expenditures')::decimal;
         allocated := (_budget->>'initialAllocation')::decimal + (_budget->>'allocationTo')::decimal - (_budget->>'allocationFrom')::decimal;
         totalFunding := allocated + (_budget->>'netTransfers')::decimal;
         unavailable := (_budget->>'encumbered')::decimal + expended + (_budget->>'awaitingPayment')::decimal;
         available := totalFunding - unavailable;
         cashBalance := totalFunding - expended;

         IF
            (budget_rollover->>'rolloverAllocation')::boolean
         THEN
            newAllocated := allocated;
         ELSE
            newAllocated := 0;
         END IF;

         newNetTransfers := CASE budget_rollover->>'rolloverBudgetValue'
                                WHEN 'Available'   THEN available
                                WHEN 'CashBalance' THEN cashBalance
                                ELSE 0
                            END;

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
            toFiscalYear					 jsonb;
            fromFiscalYear				 jsonb;
            temprow 						   record;
            exceptionText 			   text;
            exceptionDetails			 text;
            input_fromFiscalYearId uuid := _rollover_record->>'fromFiscalYearId';
            input_toFiscalYearId   uuid := _rollover_record->>'toFiscalYearId';
            input_ledgerId         uuid := _rollover_record->>'ledgerId';
    BEGIN


        SELECT INTO toFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE input_toFiscalYearId=id;
        SELECT INTO fromFiscalYear (jsonb::jsonb) FROM ${myuniversity}_${mymodule}.fiscal_year WHERE input_fromFiscalYearId=id;

        CREATE TEMPORARY TABLE tmp_budget(LIKE ${myuniversity}_${mymodule}.budget);

        -- #1 Upsert budgets
        INSERT INTO tmp_budget
            (
                SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER5', budget.id, fund.id)),
                 ${myuniversity}_${mymodule}.build_budget(budget.jsonb, fund.jsonb, _rollover_record, toFiscalYear)
                FROM ${myuniversity}_${mymodule}.budget AS budget
                INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id=budget.fundId
                WHERE fund.jsonb->>'fundStatus'<>'Inactive' AND budget.fiscalYearId=input_fromFiscalYearId AND fund.ledgerId=input_ledgerId
            );

        INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_budget
            (
                SELECT id, budget || jsonb_build_object(
                    'ledgerRolloverId', _rollover_record->>'id',
                    'budgetId', id,
                    'fundDetails', jsonb_build_object(
                        'id', fund->'id',
                        'name', fund->'name',
                        'code', fund->'code',
                        'fundStatus', fund->'fundStatus',
                        'fundTypeId', fund->'fundTypeId',
                        'fundTypeName', fund_type->'name',
                        'acqUnitIds', fund->'acqUnitIds',
                        'allocatedFromIds', fund->'allocatedFromIds',
                        'allocatedFromNames', allocatedFromNames,
                        'allocatedToIds', fund->'allocatedToIds',
                        'allocatedToNames', allocatedToNames,
                        'externalAccountNo', fund->'externalAccountNo',
                        'description', fund->'description'))
            FROM
                (
                    SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER5', budget.id, _rollover_record->>'id')) AS id,
                           budget.jsonb AS budget, fund.jsonb AS fund, fund_type.jsonb AS fund_type,
                    (
                        SELECT json_agg(inner_fund.jsonb->>'name') FROM ${myuniversity}_${mymodule}.fund AS inner_fund
                        WHERE (fund.jsonb->'allocatedFromIds')::jsonb ? inner_fund.id::text
                    ) AS allocatedFromNames,
                    (
                        SELECT json_agg(inner_fund.jsonb->>'name') FROM ${myuniversity}_${mymodule}.fund AS inner_fund
                        WHERE (fund.jsonb->'allocatedToIds')::jsonb ? inner_fund.id::text
                    ) AS allocatedToNames
                    FROM (
                             SELECT COALESCE(budget.id, tmp_budget.id) as id,
                                    CASE
                                        WHEN budget.id IS NULL THEN tmp_budget.jsonb
                                        ELSE budget.jsonb || jsonb_build_object
                                          (
                                              'budgetStatus', 'Active',
                                              'allocationTo', (budget.jsonb->>'allocationTo')::decimal + (tmp_budget.jsonb->>'initialAllocation')::decimal,
                                              'netTransfers', (budget.jsonb->>'netTransfers')::decimal + (tmp_budget.jsonb->>'netTransfers')::decimal,
                                              'metadata', budget.jsonb->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                                    END as jsonb
                             FROM tmp_budget as tmp_budget
                             LEFT JOIN ${myuniversity}_${mymodule}.budget as budget
                                 ON tmp_budget.fundId = budget.fundId AND tmp_budget.fiscalYearId = budget.fiscalYearId
                             WHERE budget.fiscalYearId = input_toFiscalYearId OR budget.id IS NULL
                         ) as budget
                    LEFT JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id = (budget.jsonb->>'fundId')::uuid
                    LEFT JOIN ${myuniversity}_${mymodule}.fund_type AS fund_type ON fund_type.id = fund.fundTypeId
                ) AS subquery
            );

        IF _rollover_record->>'rolloverType' <> 'Preview' THEN
            INSERT INTO ${myuniversity}_${mymodule}.budget
                (
                    SELECT id, jsonb FROM tmp_budget
                )
                ON CONFLICT (lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fundId'::text)), lower(${myuniversity}_${mymodule}.f_unaccent(jsonb ->> 'fiscalYearId'::text)))
                     DO UPDATE SET jsonb=${myuniversity}_${mymodule}.budget.jsonb || jsonb_build_object
                        (
                            'allocationTo', (${myuniversity}_${mymodule}.budget.jsonb->>'allocationTo')::decimal + (EXCLUDED.jsonb->>'initialAllocation')::decimal,
                            'netTransfers', (${myuniversity}_${mymodule}.budget.jsonb->>'netTransfers')::decimal + (EXCLUDED.jsonb->>'netTransfers')::decimal,
                            'metadata', ${myuniversity}_${mymodule}.budget.jsonb->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text));

            UPDATE ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_budget as rollover_budget
            SET jsonb = rollover_budget.jsonb || jsonb_build_object('budgetId', budget.id)
            FROM ${myuniversity}_${mymodule}.budget as budget
            WHERE budget.fundId = rollover_budget.fundId
                AND budget.fiscalYearId = rollover_budget.fiscalYearId
                AND rollover_budget.ledgerRolloverId::text = _rollover_record->>'id';
        END IF;

        DROP TABLE IF EXISTS tmp_budget;

        -- #1.1 Create budget expense class relations for new budgets
        CREATE TEMPORARY TABLE tmp_budget_expense_class(LIKE ${myuniversity}_${mymodule}.budget_expense_class);
        INSERT INTO tmp_budget_expense_class
        SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER6', oldBudget.id, fund.id, newBudget.id, exp.id)),
               jsonb_build_object('budgetId', newBudget.jsonb ->> 'budgetId',
                                  'expenseClassId', exp.jsonb->>'expenseClassId',
                                  'status', exp.jsonb->>'status')
        FROM ${myuniversity}_${mymodule}.budget AS oldBudget
               INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id = oldBudget.fundId
               INNER JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_budget AS newBudget ON newBudget.fundId = oldBudget.fundId
               INNER JOIN ${myuniversity}_${mymodule}.budget_expense_class AS exp ON oldBudget.id = exp.budgetid
        WHERE oldBudget.fiscalYearId = input_fromFiscalYearId
          AND fund.ledgerId = input_ledgerId
          AND newBudget.fiscalYearId = input_toFiscalYearId
          AND newBudget.ledgerRolloverId = (_rollover_record->>'id')::uuid;

        IF _rollover_record->>'rolloverType' <> 'Preview' THEN
            INSERT INTO ${myuniversity}_${mymodule}.budget_expense_class(SELECT id, jsonb FROM tmp_budget_expense_class)
                ON CONFLICT DO NOTHING;

            -- #1.2 Create budget groups relation for new budgets
            INSERT INTO ${myuniversity}_${mymodule}.group_fund_fiscal_year
            SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER7', oldBudget.id, fund.id, newBudget.id, gr.id)),
                   jsonb_build_object('budgetId', newBudget.id,
                                      'groupId', gr.jsonb->>'groupId',
                                      'fiscalYearId', input_toFiscalYearId,
                                      'fundId', gr.jsonb->>'fundId')
            FROM ${myuniversity}_${mymodule}.budget AS oldBudget
                     INNER JOIN ${myuniversity}_${mymodule}.fund AS fund ON fund.id = oldBudget.fundId
                     INNER JOIN ${myuniversity}_${mymodule}.budget AS newBudget ON newBudget.fundId = oldBudget.fundId
                     INNER JOIN ${myuniversity}_${mymodule}.group_fund_fiscal_year AS gr ON oldBudget.id = gr.budgetid
            WHERE oldBudget.fiscalYearId = input_fromFiscalYearId
              AND fund.ledgerId = input_ledgerId
              AND newBudget.fiscalYearId = input_toFiscalYearId
            ON CONFLICT DO NOTHING;
        END IF;

        CREATE TEMPORARY TABLE tmp_transaction(LIKE ${myuniversity}_${mymodule}.transaction);

         -- #2 Create allocations
        INSERT INTO tmp_transaction
             (
                SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER8', budget.jsonb->>'id')),
                    jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', input_toFiscalYearId, 'transactionType', 'Allocation',
                    'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'initialAllocation')::decimal+
                        (budget.jsonb->>'allocationTo')::decimal-
                        (budget.jsonb->>'allocationFrom')::decimal-
                        sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                    'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                FROM ${myuniversity}_${mymodule}.budget AS budget
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Allocation'
                LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Allocation'
                LEFT JOIN ${myuniversity}_${mymodule}.fund AS fund_to ON budget.fundId=fund_to.id
                WHERE budget.fiscalYearId=input_toFiscalYearId AND fund_to.ledgerId=input_ledgerId
                GROUP BY budget.jsonb
                HAVING (budget.jsonb->>'initialAllocation')::decimal+(budget.jsonb->>'allocationTo')::decimal-(budget.jsonb->>'allocationFrom')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
             );

        -- #3 Create transfers
        INSERT INTO tmp_transaction
              (
                 SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER9', budget.jsonb->>'id')), jsonb_build_object('toFundId', budget.jsonb->>'fundId', 'fiscalYearId', input_toFiscalYearId, 'transactionType', 'Rollover transfer',
                                                               'source', 'User', 'currency', toFiscalYear->>'currency', 'amount', (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)),
                                                               'metadata', _rollover_record->'metadata' || jsonb_build_object('createdDate', date_trunc('milliseconds', clock_timestamp())::text))
                 FROM ${myuniversity}_${mymodule}.budget AS budget
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_to ON budget.fundId=tr_to.toFundId  AND budget.fiscalYearId=tr_to.fiscalYearId AND  tr_to.jsonb->>'transactionType'='Transfer'
                 LEFT JOIN ${myuniversity}_${mymodule}.transaction AS tr_from ON budget.fundId=tr_from.fromFundId AND budget.fiscalYearId=tr_from.fiscalYearId AND tr_from.jsonb->>'transactionType'='Transfer'
                 LEFT JOIN ${myuniversity}_${mymodule}.fund AS fund_to ON budget.fundId=fund_to.id
                 WHERE budget.fiscalYearId=input_toFiscalYearId AND fund_to.ledgerId=input_ledgerId
                 GROUP BY budget.jsonb
                 HAVING (budget.jsonb->>'netTransfers')::decimal-sum(COALESCE((tr_to.jsonb->>'amount')::decimal, 0.00))+sum(COALESCE((tr_from.jsonb->>'amount')::decimal, 0.00)) <> 0
              );

        IF _rollover_record->>'rolloverType' <> 'Preview' THEN
            INSERT INTO ${myuniversity}_${mymodule}.transaction SELECT * FROM tmp_transaction;
        END IF;

        DROP TABLE IF EXISTS tmp_transaction;

        -- #4.1 - create table to accumulate encumbered transactions, that will be used for Preview rollover
        CREATE TEMPORARY TABLE tmp_encumbered_transactions(LIKE ${myuniversity}_${mymodule}.transaction);

        -- #4.2 sort order ids
        FOR temprow IN
            SELECT min(tr.jsonb->'metadata'->>'createdDate') date, tr.jsonb->'encumbrance'->>'sourcePurchaseOrderId' order_id FROM ${myuniversity}_${mymodule}.transaction tr
                LEFT JOIN ${myuniversity}_${mymodule}.fund fund ON fund.id = tr.fromFundId
                LEFT JOIN ${myuniversity}_${mymodule}.ledger ledger ON ledger.id=fund.ledgerId
                WHERE tr.jsonb->>'transactionType' = 'Encumbrance'
                    AND tr.fiscalYearId = input_fromFiscalYearId
                    AND tr.jsonb->'encumbrance'->>'orderStatus' = 'Open'
                    AND (tr.jsonb->'encumbrance'->>'reEncumber')::boolean
                    AND ledger.id=input_ledgerId
                GROUP BY order_id
                ORDER BY date
        LOOP
            PERFORM ${myuniversity}_${mymodule}.rollover_order(temprow.order_id::text, _rollover_record);
        END LOOP;

        -- #13 update planned budget status to active
        IF _rollover_record->>'rolloverType' <> 'Preview' THEN
            UPDATE ${myuniversity}_${mymodule}.budget as budget
            SET jsonb = budget.jsonb || jsonb_build_object('budgetStatus', 'Active')
            FROM ${myuniversity}_${mymodule}.fund as fund
            WHERE budget.fundId = fund.id
                AND fund.ledgerId = input_ledgerId
                AND budget.fiscalYearId = input_toFiscalYearId
                AND budget.jsonb ? 'budgetStatus'
                AND budget.jsonb ->> 'budgetStatus'='Planned';
        END IF;

        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS exceptionText = MESSAGE_TEXT,
                                    exceptionDetails = PG_EXCEPTION_DETAIL;
            INSERT INTO ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_error (id, jsonb)
                SELECT public.uuid_generate_v5(public.uuid_nil(), concat('BER3', _rollover_record->>'id')), jsonb_build_object
                (
                  'ledgerRolloverId', _rollover_record->>'id',
                  'errorType', 'Other',
                  'failedAction', exceptionText,
                  'errorMessage', exceptionDetails
                );

    END;
$$ LANGUAGE plpgsql;

-- remove old functions
DROP FUNCTION IF EXISTS public.calculate_planned_encumbrance_amount(jsonb, jsonb);
