CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.budget_financial_summary_view
AS
SELECT id,
       jsonb ||
       jsonb_build_object('allocated', allocated) ||
       jsonb_build_object('totalFunding', totalFunding) ||
       jsonb_build_object('cashBalance', cashBalance) ||
       jsonb_build_object('overEncumbrance', overEncumbrance) ||
       jsonb_build_object('overExpended', overExpended) ||
       jsonb_build_object('available', available) ||
       jsonb_build_object('unavailable', unavailable)
           as jsonb,
       fundid,
       fiscalyearid,
       creation_date
FROM (SELECT id,
             jsonb,
             fundid,
             fiscalyearid,
             allocated,
             totalFunding,
             cashBalance,
             overExpended,
             overEncumbrance,
             available,
             unavailable,
             creation_date
      FROM ${myuniversity}_${mymodule}.budget,
           lateral (select (jsonb ->> 'initialAllocation')::decimal initialAllocation) ia,
           lateral (select (jsonb ->> 'allocationTo')::decimal allocationTo) at,
           lateral (select (jsonb ->> 'allocationFrom')::decimal allocationFrom) af,
           lateral (select (coalesce((jsonb ->> 'expenditures')::decimal, 0)) expenditures) ex,
           lateral (select (coalesce((jsonb ->> 'awaitingPayment')::decimal, 0)) awaitingPayment) ap,
           lateral (select (jsonb ->> 'encumbered')::decimal encumbered) en,
           lateral (select (coalesce((jsonb ->> 'netTransfers')::decimal, 0)) netTransfers) nt,
           lateral (select initialAllocation + allocationTo - allocationFrom allocated) al,
           lateral (select allocated + netTransfers as totalFunding) tf,
           lateral (select totalFunding - expenditures as cashBalance) cb,
           lateral (select greatest(encumbered - greatest(greatest(totalFunding - expenditures, 0) - awaitingPayment, 0), 0) overEncumbrance) oen,
           lateral (select greatest(awaitingPayment + expenditures - totalFunding , 0) overExpended) oex,
           lateral (select encumbered + awaitingPayment + expenditures unavailable) un,
           lateral (select greatest(totalFunding - unavailable, 0) available) av) calc;
