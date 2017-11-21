-- SQL script to create the tenant/schema

create role diku_mod_funds PASSWORD 'password' NOSUPERUSER NOCREATEDB INHERIT LOGIN;
create schema diku_mod_funds authorization diku_mod_funds;
grant all privileges on all tables in schema diku_mod_funds to current_user;
grant all privileges on all tables in schema diku_mod_funds to diku_mod_funds;

set search_path to diku_mod_funds, public;

/**
 * Initialization
 */
set client_encoding to 'UTF8';
set standard_conforming_strings to on;
set check_function_bodies to false;
set client_min_messages to warning;

/**
 * Import third-party modules that will allow for UUID generation, if it is not installed yet.
 */
create extension if not exists "pgcrypto";

create table if not exists ledger (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_ledger on ledger using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_ledger()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  return new;
end;
$$ language 'plpgsql';
create trigger update_timestamp_ledger
before insert or update on ledger
for each row execute procedure update_modified_column_ledger();


create table if not exists fund (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp,
    "ledger_id" uuid references ledger
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_fund on fund using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_fund()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  new.ledger_id = new.jsonb->>'ledger_id';
  return new;
end;
$$ language 'plpgsql';
create trigger update_fund
before insert or update on fund
for each row execute procedure update_modified_column_fund();


create table if not exists fiscal_year (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_fiscal_year on fiscal_year using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_fiscal_year()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  return new;
end;
$$ language 'plpgsql';
create trigger update_timestamp_fiscal_year
before insert or update on fund
for each row execute procedure update_modified_column_fiscal_year();


create table if not exists budget (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp,
    "fund_id" uuid references fund,
    "fiscal_year_id" uuid references fiscal_year
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_budget on budget using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_budget()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  new.fund_id = new.jsonb->>'fund_id';
  new.fiscal_year_id = new.jsonb->>'fiscal_year_id';
  return new;
end;
$$ language 'plpgsql';
create trigger update_budget
before insert or update on budget
for each row execute procedure update_modified_column_budget();


create table if not exists fund_distribution (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp,
    "budget_id" uuid references budget
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_fund_distribution on fund_distribution using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_fund_distribution()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  new.budget_id = new.jsonb->>'budget_id';
  return new;
end;
$$ language 'plpgsql';
create trigger update_fund_distribution
before insert or update on fund_distribution
for each row execute procedure update_modified_column_fund_distribution();


create table if not exists tag (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_tag on tag using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_tag()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  return new;
end;
$$ language 'plpgsql';
create trigger update_timestamp_tag
before insert or update on tag
for each row execute procedure update_modified_column_tag();


create table if not exists "transaction" (
    "_id" uuid primary key default gen_random_uuid(),
    "jsonb" jsonb,
    "creation_date" date not null default current_timestamp,
    "update_date" date not null default current_timestamp,
    "budget_id" uuid references budget
);
-- index to support @> ops, faster than jsonb_ops
create index idxgin_transaction on "transaction" using gin (jsonb jsonb_path_ops);

-- update the update_date column when the record is updated
create or replace function update_modified_column_transaction()
returns trigger as $$
begin
  new.update_date = current_timestamp;
  new.budget_id = new.jsonb->>'budget_id';
  return new;
end;
$$ language 'plpgsql';
create trigger update_transaction
before insert or update on "transaction"
for each row execute procedure update_modified_column_transaction();
