## 4.1.0 - Unreleased

## 4.0.0 - Released
[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v3.0.0...v4.0.0)

### Stories
* [MODFISTO-59](https://issues.folio.org/browse/MODFISTO-59)  Define and Implement FundType API	
* [MODFISTO-56](https://issues.folio.org/browse/MODFISTO-56)  Support all-or-nothing operations for encumbrances by order
* [MODFISTO-55](https://issues.folio.org/browse/MODFISTO-55)  Create order_transaction_summaries API
* [MODFISTO-54](https://issues.folio.org/browse/MODFISTO-54)  Encumbrance Schema Updates
* [MODFISTO-50](https://issues.folio.org/browse/MODFISTO-50)  GroupFiscalYearSummary Schema
* [MODFISTO-49](https://issues.folio.org/browse/MODFISTO-49)  Ledger/LedgerFY updates: Schema changes
* [MODFISTO-48](https://issues.folio.org/browse/MODFISTO-48)  Adjustments to allow listing budgets by group/fiscalYear/ledger
* [MODFISTO-46](https://issues.folio.org/browse/MODFISTO-46)  Update transaction schema
* [MODFISTO-42](https://issues.folio.org/browse/MODFISTO-42)  Finance Records: Inheriting status from parent
* [MODFISTO-41](https://issues.folio.org/browse/MODFISTO-41)  Data migration scripts for recent schema changes
* [MODFISTO-38](https://issues.folio.org/browse/MODFISTO-38)  Create GroupFundFY API
* [MODFISTO-37](https://issues.folio.org/browse/MODFISTO-37)  Update LedgerFY table
* [MODFISTO-35](https://issues.folio.org/browse/MODFISTO-35)  Update GroupFundFY table
* [MODFISTO-34](https://issues.folio.org/browse/MODFISTO-34)  Spike: Avoiding partially paid invoices POC
* [MODFISTO-33](https://issues.folio.org/browse/MODFISTO-33)  Define and implement the GroupFundFY API
* [MODFISTO-32](https://issues.folio.org/browse/MODFISTO-32)  Define and implement the LedgerFY API
* [MODFISTO-31](https://issues.folio.org/browse/MODFISTO-31)  Add support for querying Budget API by fund/ledger/fiscalYear fields
* [MODFISTO-29](https://issues.folio.org/browse/MODFISTO-29)  Transaction Calculations - Encumbrance
* [MODFISTO-27](https://issues.folio.org/browse/MODFISTO-27)  Transaction Calculations - Transfers
* [MODFISTO-26](https://issues.folio.org/browse/MODFISTO-26)  Transaction Calculations - Allocations
* [MODFISTO-25](https://issues.folio.org/browse/MODFISTO-25)  Update FiscalYear schema
* [MODFISTO-24](https://issues.folio.org/browse/MODFISTO-24)  Update Budget Schema
* [MODFISTO-23](https://issues.folio.org/browse/MODFISTO-23)  Define and implement group API
* [MODFISTO-22](https://issues.folio.org/browse/MODFISTO-22)  Create Group schemas
* [MODFISTO-21](https://issues.folio.org/browse/MODFISTO-21)  Create/Update Ledger schemas
* [MODFISTO-20](https://issues.folio.org/browse/MODFISTO-20)  Update Transaction schemas
* [MODFISTO-19](https://issues.folio.org/browse/MODFISTO-19)  Update fund schema
* [MODFISTO-18](https://issues.folio.org/browse/MODFISTO-18)  Define and Implement FundType API

### Bug Fixes
* [MODFISTO-52](https://issues.folio.org/browse/MODFISTO-52)  Unavailable increasing when allocating from budget to budget
* [MODFISTO-43](https://issues.folio.org/browse/MODFISTO-43)  Return error if specified Fund does not have a budget in the current fiscal year upon transaction processing


## 3.0.0 - Released
This release contains only changes about removing acquisitionsUnit from fund schema

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v2.0.0...v3.0.0)

### Stories
* [MODFISTO-14](https://issues.folio.org/browse/MODFISTO-14) Remove fund.acquisitionsUnit


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
