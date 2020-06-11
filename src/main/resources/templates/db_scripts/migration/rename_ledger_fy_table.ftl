<#if mode.name() == "UPDATE">
  ALTER TABLE IF EXISTS ${myuniversity}_${mymodule}.ledgerFy RENAME TO ledger_fy;
</#if>
