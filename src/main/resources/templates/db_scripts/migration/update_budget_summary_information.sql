UPDATE ${myuniversity}_${mymodule}.budget
set jsonb = jsonb ||
            jsonb_build_object('initialAllocation', (coalesce((SELECT jsonb ->> 'amount'
                                                               FROM ${myuniversity}_${mymodule}.transaction
                                                               WHERE jsonb -> 'transactionType' = '"Allocation"'
                                                                 and jsonb -> 'fiscalYearId' = budget.jsonb -> 'fiscalYearId'
                                                                 and jsonb -> 'toFundId' = budget.jsonb -> 'fundId'
                                                               order by creation_date
                                                               limit 1), '0'))::decimal) ||
            jsonb_build_object('allocationTo', (coalesce((SELECT sum(amount.amount)
                                                          FROM (select (jsonb ->> 'amount')::decimal as amount
                                                                from ${myuniversity}_${mymodule}.transaction
                                                                WHERE jsonb -> 'transactionType' = '"Allocation"'
                                                                  and jsonb -> 'fiscalYearId' = budget.jsonb -> 'fiscalYearId'
                                                                  and jsonb -> 'toFundId' = budget.jsonb -> 'fundId'
                                                                order by creation_date
                                                                offset 1) as amount), '0'))) ||
            jsonb_build_object('allocationFrom', (coalesce((SELECT sum(amount.amount)
                                                            FROM (select (jsonb ->> 'amount')::decimal as amount
                                                                  from ${myuniversity}_${mymodule}.transaction
                                                                  WHERE jsonb -> 'transactionType' = '"Allocation"'
                                                                    and jsonb -> 'fiscalYearId' = budget.jsonb -> 'fiscalYearId'
                                                                    and jsonb -> 'fromFundId' = budget.jsonb -> 'fundId'
                                                                  order by creation_date) as amount), '0')))
                #- '{allocated}'
                #- '{available}'
                #- '{unavailable}'
                #- '{overExpended}'
                #- '{totalFunding}'
                #- '{cashBalance}'
    #- '{overEncumbrance}';
