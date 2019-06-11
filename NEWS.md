## 2.1.0 - Unreleased

## 2.0.0 - Released
This release contains implementation of `encumbrance` and `fund` endpoints, finance schemas updates, created 
unique index on fund `code` column.

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v1.1.0...v2.0.0)

### Stories
* [MODFISTO-12](https://issues.folio.org/browse/MODFISTO-12) Implemented fund API
* [MODFISTO-11](https://issues.folio.org/browse/MODFISTO-11) Implemented encumbrance API
* [MODFISTO-10](https://issues.folio.org/browse/MODFISTO-10) Encumbrance schema updated
* [MODFISTO-9](https://issues.folio.org/browse/MODFISTO-9) Unique index created on the fund code column
* [MODFISTO-8](https://issues.folio.org/browse/MODFISTO-8) Fund schema updated

### Bug Fixes
* [MODFISTO-15](https://issues.folio.org/browse/MODFISTO-15) Updated fiscal year schema


## 1.1.0 - Released
The primary focus of this release was to enable loading sample data by TenantAPI from Raml Module Builder v.23

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v1.0.1...v1.1.0)

### Stories
* [MODFISTO-5](https://issues.folio.org/browse/MODFISTO-5) Use loadSample/loadReference to load sample and reference data
* [MODFISTO-1](https://issues.folio.org/browse/MODFISTO-1) Update to RAML 1.0 and RMB 23

## 1.0.1
* Model updates to support additional UI functionality

## 1.0.0
CRUD APIs for the following endpoints:
* `/budget`
* `/fiscal_year`
* `/fund`
* `/fund_distribution`
* `/ledger`
* `/tag`
* `/transaction`
