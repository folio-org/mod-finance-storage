<#if mode.name() == "UPDATE">
  -- update ledger fiscal year records

  UPDATE ${myuniversity}_${mymodule}.ledgerFy as ledger_fy
  SET
    jsonb = jsonb || jsonb_build_object('available', (jsonb->>'available')::decimal - sub.total,
      'unavailable', (jsonb->>'unavailable')::decimal + sub.total)
  FROM (SELECT ledger.id AS ledgerId, transactions.fiscalYearId AS fiscalYearId, sum((transactions.jsonb->>'amount')::decimal) AS total
    FROM ${myuniversity}_${mymodule}.transaction AS transactions
    LEFT JOIN ${myuniversity}_mod_invoice_storage.invoices AS invoices ON transactions.jsonb->>'sourceInvoiceId'=invoices.id::text
    LEFT JOIN ${myuniversity}_${mymodule}.fund AS fund ON transactions.fromFundId = fund.id
    LEFT JOIN ${myuniversity}_${mymodule}.ledger AS ledger ON fund.ledgerId = ledger.id
    WHERE invoices.jsonb->>'status' = 'Approved'
      AND transactions.jsonb->>'transactionType'='Pending payment'
      AND NOT transactions.jsonb ? 'awaitingPayment'
    GROUP BY ledger.id, transactions.fiscalYearId) AS sub
  WHERE ledger_fy.fiscalYearId=sub.fiscalYearId AND ledger_fy.ledgerId=sub.ledgerId;

</#if>
