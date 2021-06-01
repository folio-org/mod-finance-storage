<#if mode.name() == "UPDATE">
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
-- rename numEncumbrances to numPendingPayments in invoice_transaction_summary table and mark numPendingPayments as processed
UPDATE ${myuniversity}_${mymodule}.invoice_transaction_summaries
	SET jsonb = jsonb - '{numEncumbrances}'::text[] || jsonb_build_object('numPendingPayments', -(jsonb->>'numPaymentsCredits')::integer)
WHERE jsonb ? 'numEncumbrances';

-- Create pending payments from invoice lines
INSERT INTO ${myuniversity}_${mymodule}.transaction
SELECT public.uuid_generate_v5(public.uuid_nil(), concat('PPCM1', fdi.il->>'id', invoices.id, vouchers.id, budget.id, fy.id)),
                                              jsonb_strip_nulls(jsonb_build_object('transactionType', 'Pending payment', 'fromFundId', fd->>'fundId',
                                              'amount', fdi.amount*(vouchers.jsonb->>'exchangeRate')::decimal, 'source', 'Invoice',
                                              'sourceInvoiceId', invoices.id, 'sourceInvoiceLineId', fdi.il->>'id',
                                              'fiscalYearId', budget.fiscalYearId, 'currency', fy.jsonb->>'currency',
                                              'awaitingPayment',
                                              CASE WHEN fdi.fd ?'encumbrance'
                                              THEN jsonb_build_object('encumbranceId', fdi.fd->>'encumbrance', 'releaseEncumbrance', fdi.il->>'releaseEncumbrance')
                                              ELSE null END))
FROM (SELECT fd,
              (CASE WHEN fd->>'distributionType'='amount' THEN (fd->>'value')::decimal
                ELSE ((i.jsonb->>'total')::decimal*(fd->>'value')::decimal)/100 END) AS amount,
              i.jsonb AS il
      FROM ${myuniversity}_mod_invoice_storage.invoice_lines AS i,
      jsonb_array_elements(i.jsonb->'fundDistributions') AS fd) AS fdi
LEFT JOIN ${myuniversity}_mod_invoice_storage.invoices AS invoices ON invoices.id::text = fdi.il->>'invoiceId'
LEFT JOIN ${myuniversity}_mod_invoice_storage.vouchers AS vouchers ON invoices.id=vouchers.invoiceId
LEFT JOIN ${myuniversity}_${mymodule}.budget AS budget ON budget.fundId::text = fdi.fd->>'fundId'
LEFT JOIN ${myuniversity}_${mymodule}.fiscal_year AS fy ON fy.id=budget.fiscalYearId
WHERE invoices.jsonb->>'status' = 'Approved' AND budget.jsonb->>'budgetStatus'='Active';

-- Create pending payments not linked from invoices
INSERT INTO ${myuniversity}_${mymodule}.transaction
SELECT public.uuid_generate_v5(public.uuid_nil(), concat('PPCM2', fdi.invoice_id, budget.id, vouchers.id, fy.id)),
                        jsonb_build_object('transactionType', 'Pending payment', 'fromFundId', fd->>'fundId',
											  'amount', fdi.amount*(vouchers.jsonb->>'exchangeRate')::decimal, 'source', 'Invoice',
											  'sourceInvoiceId', invoice_id,
											  'fiscalYearId', budget.fiscalYearId, 'currency', fy.jsonb->>'currency')
FROM (SELECT fd, (CASE WHEN fd->>'distributionType'='amount' THEN (fd->>'value')::decimal
                    ELSE ((CASE WHEN adj->>'type'='Amount' THEN (adj->>'value')::decimal
                          ELSE (adj->>'value')::decimal*(invoices.jsonb->>'subTotal')::decimal/100 END)*(fd->>'value')::decimal)/100 END) AS amount,
            invoices.id AS invoice_id
  FROM ${myuniversity}_mod_invoice_storage.invoices AS invoices,
	jsonb_array_elements(invoices.jsonb->'adjustments') AS adj,
	jsonb_array_elements( adj->'fundDistributions') AS fd
  WHERE invoices.jsonb->>'status' = 'Approved') AS fdi
LEFT JOIN ${myuniversity}_${mymodule}.budget AS budget ON budget.fundId::text = fdi.fd->>'fundId'
LEFT JOIN ${myuniversity}_mod_invoice_storage.vouchers AS vouchers ON invoice_id=vouchers.invoiceId
LEFT JOIN ${myuniversity}_${mymodule}.fiscal_year AS fy ON fy.id=budget.fiscalYearId
WHERE budget.jsonb->>'budgetStatus'='Active';

-- Update budgets
UPDATE ${myuniversity}_${mymodule}.budget
  SET
    jsonb = jsonb || jsonb_build_object('available', (jsonb->>'available')::decimal - sub.total,
                                        'unavailable', (jsonb->>'unavailable')::decimal + sub.total,
                                        'awaitingPayment', (jsonb->>'awaitingPayment')::decimal + sub.total)
  FROM (SELECT  budget.id AS budget_id, sum((transactions.jsonb->>'amount')::decimal) AS total
        FROM ${myuniversity}_${mymodule}.transaction AS transactions
        LEFT JOIN ${myuniversity}_mod_invoice_storage.invoices AS invoices ON transactions.jsonb->>'sourceInvoiceId'=invoices.id::text
        LEFT JOIN ${myuniversity}_${mymodule}.budget AS budget ON transactions.fromFundId = budget.fundId AND transactions.fiscalYearId = budget.fiscalYearId
        WHERE transactions.jsonb->>'transactionType'='Pending payment'
          AND NOT transactions.jsonb ? 'awaitingPayment'
          AND invoices.jsonb->>'status'='Approved'
          GROUP BY budget.id) AS sub
  WHERE id=sub.budget_id;

</#if>
