CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.job_number (
  type varchar(255) NOT NULL,
  last_number bigint NOT NULL DEFAULT 0,
  PRIMARY KEY(type)
);

INSERT INTO ${myuniversity}_${mymodule}.job_number (type, last_number)
VALUES ('FundUpdateLogs', 0)
ON CONFLICT (type) DO NOTHING;
