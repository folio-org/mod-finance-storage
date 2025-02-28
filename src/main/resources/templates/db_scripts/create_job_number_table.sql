CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.job_number (
  last_number bigint NOT NULL DEFAULT 1,
  type varchar(255) NOT NULL
);
INSERT INTO ${myuniversity}_${mymodule}.job_number (last_number, type)
SELECT 1, 'Logs'
WHERE NOT EXISTS (SELECT * FROM ${myuniversity}_${mymodule}.job_number);
