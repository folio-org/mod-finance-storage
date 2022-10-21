CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.ledger_rollover_logs_view
AS SELECT rollover.id AS id,
          jsonb_build_object('ledgerRolloverId', rollover.id) ||
          jsonb_build_object('ledgerId', rollover.ledgerId) ||
          jsonb_build_object('startDate', progress.jsonb #>> '{metadata, createdDate}') ||
          CASE
              WHEN progress.jsonb ->> 'overallRolloverStatus' IN ('Not Started', 'In Progress') THEN '{}'::jsonb
              ELSE jsonb_build_object('endDate', progress.jsonb #>> '{metadata, updatedDate}')
          END ||
          jsonb_build_object('rolloverStatus', progress.jsonb ->> 'overallRolloverStatus') ||
          jsonb_build_object('ledgerRolloverType', rollover.jsonb ->> 'rolloverType') AS jsonb
FROM ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover AS rollover
         INNER JOIN ${myuniversity}_${mymodule}.ledger_fiscal_year_rollover_progress AS progress on rollover.id = progress.ledgerrolloverid;
