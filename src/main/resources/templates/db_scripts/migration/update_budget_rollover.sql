UPDATE ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover
SET jsonb =
      (
        -- Update each budgetsRollover element renaming 'rolloverAvailable' to 'rolloverBasedOn' and if field true update to 'Allocation' else to 'None'
        SELECT jsonb_set(jsonb, '{budgetsRollover}', jsonb_agg(rollover - 'rolloverAvailable'
          || jsonb_build_object('rolloverBudgetValue',
                                CASE WHEN (rollover -> 'rolloverAvailable')::boolean
                                     THEN 'Allocation'
                                     ELSE 'None'
                                  END)))
        FROM jsonb_array_elements(jsonb -> 'budgetsRollover') rollover
      )
WHERE jsonb_array_length(jsonb -> 'budgetsRollover') > 0;
