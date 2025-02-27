CREATE SEQUENCE IF NOT EXISTS ${myuniversity}_${mymodule}.job_number MINVALUE 0 NO MAXVALUE CACHE 1 NO CYCLE;
ALTER SEQUENCE ${myuniversity}_${mymodule}.job_number OWNER TO ${myuniversity}_${mymodule};
