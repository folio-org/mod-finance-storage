## 8.9.0 - Unreleased

## 8.8.0 - Released (Sunflower R1 2025)
The primary focus of this release was to separate Credited from Expended and improve different calculations
[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.7.0...v8.8.0)

### Stories
* [MODFISTO-497](https://folio-org.atlassian.net/browse/MODFISTO-497) Implement endpoint to return all finance data for FY
* [MODFISTO-500](https://folio-org.atlassian.net/browse/MODFISTO-500) Clean up permissions
* [MODFISTO-501](https://folio-org.atlassian.net/browse/MODFISTO-501) Implement endpoint to save FY finance data in bulk
* [MODFISTO-517](https://folio-org.atlassian.net/browse/MODFISTO-517) Create budget when adding batch allocation to fund without budget
* [MODFISTO-519](https://folio-org.atlassian.net/browse/MODFISTO-519) Only one row for unique budget should be displayed in batch allocation form if fund is included in different groups

### Bug fixes
* [MODFISTO-513](https://folio-org.atlassian.net/browse/MODFISTO-513) Optimistic locking error displays in error message instead of banner when editing the same Group in “Finance” app

### Dependencies
* Bump `raml` from `35.3.0` to `35.4.0`
* Bump `java` from `17` to `21`


## 8.7.0 - Released (Ramsons R2 2024)
The primary focus of this release was to separate Credited from Expended and improve different calculations
[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.6.0...v8.7.0)

### Stories
* [MODFISTO-499](https://folio-org.atlassian.net/browse/MODFISTO-499) Update libraries of dependant acq modules to the latest versions
* [MODFISTO-489](https://folio-org.atlassian.net/browse/MODFISTO-489) Clean up permissions
* [MODFISTO-487](https://folio-org.atlassian.net/browse/MODFISTO-487) Prevent sql injection for budget_encumbrance_rollover script
* [MODFISTO-483](https://folio-org.atlassian.net/browse/MODFISTO-483) Add credited field support to Rollover functionality
* [MODFISTO-481](https://folio-org.atlassian.net/browse/MODFISTO-481) Separate credits from expenditures in mod-finance-storage
* [MODFISTO-466](https://folio-org.atlassian.net/browse/MODFISTO-466) New transactionType indices
* [MODFISTO-460](https://folio-org.atlassian.net/browse/MODFISTO-460) Remove the old transaction API (storage)
* [MODFISTO-371](https://folio-org.atlassian.net/browse/MODFISTO-371) Improved cashBalance, available and unavailable calculations

### Bug fixes
* [MODFISTO-488](https://folio-org.atlassian.net/browse/MODFISTO-488) Fixed encumbrance changes after cancelling a payment or credit
* [MODFISTO-484](https://folio-org.atlassian.net/browse/MODFISTO-484) Fixed overEncumbrance calculation
* [MODFISTO-480](https://folio-org.atlassian.net/browse/MODFISTO-480) Old tables are not deleted

### Dependencies
* Bump `raml` from `35.2.0` to `35.3.0`
* Bump `vertx` from `4.5.4` to `4.5.10`

## 8.6.0 - Released (Quesnelia R1 2024)
The primary focus of this release was to introduce Batch Transactions API
[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.5.0...v8.6.0)

### Stories
* [MODFISTO-472](https://folio-org.atlassian.net/browse/MODFISTO-472) Change restricted expenditures calculations
* [MODFISTO-470](https://folio-org.atlassian.net/browse/MODFISTO-470) Upgrade RAML Module Builder
* [MODFISTO-458](https://folio-org.atlassian.net/browse/MODFISTO-458) Implementation for the new transaction API
* [MODFISTO-457](https://folio-org.atlassian.net/browse/MODFISTO-457) Update database API usage
* [MODFISTO-449](https://folio-org.atlassian.net/browse/MODFISTO-449) Upgrading tenant resets reference and sample records
* [MODFISTO-432](https://folio-org.atlassian.net/browse/MODFISTO-432) Implement logic to replace AllOrNothing to process list of transaction from request payload

### Bug fixes
* [MODFISTO-475](https://folio-org.atlassian.net/browse/MODFISTO-475) Wrong encumbrance calculation when paying a credit
* [MODFISTO-455](https://folio-org.atlassian.net/browse/MODFISTO-455) Failed invoice approval creates pending payment transactions and updates budget record
* [MODFISTO-452](https://folio-org.atlassian.net/browse/MODFISTO-452) Error message contains wrong fund name when moving allocation
* [MODFISTO-259](https://folio-org.atlassian.net/browse/MODFISTO-259) Releasing 2 transactions at the same time can fail to update budgets
* [MODFISTO-133](https://folio-org.atlassian.net/browse/MODFISTO-133) Remove use of "extends" property from JSON schemas, invoice_transaction_summary.json is missing an id property

### Dependencies
* Bump `raml` from `35.0.1` to `35.2.0`
* Bump `vertx` from `4.3.4` to `4.5.4`


## 8.5.0 - Released (Poppy R2 2023)
The primary focus of this release was to improve finance rollover script performance

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.4.0...v8.5.0)
### Stories
* [MODFISTO-443](https://issues.folio.org/browse/MODFISTO-443) Allow user to decrease allocation resulting in a negative available amount
* [MODFISTO-441](https://issues.folio.org/browse/MODFISTO-441) Add the ability to skip the transaction if it is from the previous year
* [MODFISTO-439](https://issues.folio.org/browse/MODFISTO-439) Make adjustments to improve finance rollover script performance
* [MODFISTO-435](https://issues.folio.org/browse/MODFISTO-435) Update to Java 17 mod-finance-storage
* [MODFISTO-405](https://issues.folio.org/browse/MODFISTO-405) Provide error code to restriction in case transaction creation when the budget status is not found/not active
* [MODFISTO-365](https://issues.folio.org/browse/MODFISTO-365) Update dependent raml-util
* [MODFISTO-287](https://issues.folio.org/browse/MODFISTO-287) Logging improvement

### Bug Fixes
* [MODFISTO-446](https://issues.folio.org/browse/MODFISTO-446) Creating budget fails with trailing junk after parameter at or near "$2F"
* [MODFISTO-442](https://issues.folio.org/browse/MODFISTO-442) Incorrect ledger summary calculation after allocation movement to 0 budget
* [MODFISTO-426](https://issues.folio.org/browse/MODFISTO-426) Split ledger order encumbrances causing rollover errors
* [MODFISTO-411](https://issues.folio.org/browse/MODFISTO-411) RMB HttpClient resource leak, use Vert.x WebClient
* [MODORDERS-865](https://issues.folio.org/browse/MODORDERS-865) Rewrite the orders rollover interaction in an asynchronous way

## 8.4.0 - Released (Orchid R1 2023)
The primary focus of this release was to replace Vertx shared lock with DB lock to increase stability

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.3.0...v8.4.0)

### Stories
* [MODFISTO-377](https://issues.folio.org/browse/MODFISTO-377) Replace Vertx shared lock with DB lock to increase stability working with transactions
* [MODFISTO-373](https://issues.folio.org/browse/MODFISTO-373) Add migration script to support cache balance changes
* [MODFISTO-371](https://issues.folio.org/browse/MODFISTO-371) Add support to rollover cash balance during FY rollover
* [MODFISTO-358](https://issues.folio.org/browse/MODFISTO-358) Logging improvement - Configuration
* [MODFISTO-290](https://issues.folio.org/browse/MODFISTO-290) Allowing "Available" to be a negative number

### Bug Fixes
* [MODFISTO-386](https://issues.folio.org/browse/MODFISTO-386) startTx DB connection not closed on failure, resource leak
* [MODFISTO-384](https://issues.folio.org/browse/MODFISTO-384) Failed to create pending payment during invoice approval
* [MODFISTO-362](https://issues.folio.org/browse/MODFISTO-362) Expense class code and status are not shown in test rollover results .csv file

## 8.3.0 - Released (Nolana R3 2022)
The primary focus of this release was to implementation preview rollover flow

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.2.0...v8.3.0)

### Stories
* [MODFISTO-353](https://issues.folio.org/browse/MODFISTO-353) Upgrade RAML Module Builder
* [MODFISTO-351](https://issues.folio.org/browse/MODFISTO-351) Provide expense class details in Ledger Rollover Budgets API response
* [MODFISTO-348](https://issues.folio.org/browse/MODFISTO-348) Fix randomly failing tests
* [MODFISTO-338](https://issues.folio.org/browse/MODFISTO-338) Update planned budget status to active during FY rollover
* [MODFISTO-335](https://issues.folio.org/browse/MODFISTO-335) Implement sending email and include it in the preview ledger rollover flow
* [MODFISTO-327](https://issues.folio.org/browse/MODFISTO-327) Replace unique index in the table "ledger_fiscal_year_rollover" with uniqueness logic in the code
* [MODFISTO-323](https://issues.folio.org/browse/MODFISTO-323) Update ledger rollover SQL script with preview rollover logic and store results in the preview rollover table
* [MODFISTO-320](https://issues.folio.org/browse/MODFISTO-320) Define and implement Storage API : Ledger Rollover Budgets
* [MODFISTO-319](https://issues.folio.org/browse/MODFISTO-319) Define schemas and table for storing generated Budgets and schema for showing rollover logs
* [MODFISTO-318](https://issues.folio.org/browse/MODFISTO-318) Implement Preview rollover flow and update budgets with all calculated amounts


### Bug Fixes
* [MODFISTO-354](https://issues.folio.org/browse/MODFISTO-354) Encumbered amount is increased by the next test rollover Encumbered amount when run test rollover multiple times
* [MODFISTO-345](https://issues.folio.org/browse/MODFISTO-345) Remove update_encumbrances_order_status.ftl (cross module)
* [MODFISTO-343](https://issues.folio.org/browse/MODFISTO-343) Tenant init fails on 2nd time
* [MODFISTO-342](https://issues.folio.org/browse/MODFISTO-342) Tenant operation failed for module mod-finance-storage-8.1.6: SQL error
* [MODFISTO-340](https://issues.folio.org/browse/MODFISTO-340) Invalid usage of optimistic locking


## 8.2.0 - Released (Morning Glory R2 2022)
The primary focus of this release was to fix rollover issues

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.1.0...v8.2.0)

### Stories
* [MODFISTO-317](https://issues.folio.org/browse/MODFISTO-317) Upgrade RAML Module Builder
* [MODFISTO-304](https://issues.folio.org/browse/MODFISTO-304) Lock budgets to update totals

### Bug Fixes
* [MODFISTO-311](https://issues.folio.org/browse/MODFISTO-311) Redundant "Rollover transfer" transactions are created during rollover for previously rolled over ledgers
* [MODFISTO-309](https://issues.folio.org/browse/MODFISTO-309) Budgets not created during FYRO for some ledgers
* [MODFISTO-299](https://issues.folio.org/browse/MODFISTO-299) Order and financial rollover errors are not reported
* [MODFISTO-298](https://issues.folio.org/browse/MODFISTO-298) Unable to access adjustment ledger
* [MODFISTO-214](https://issues.folio.org/browse/MODFISTO-214) Illegal cross-module *_mod_finance_storage.fund access on migration


## 8.1.0 - Released
The primary focus of this release was to voiding transactions support and fix encumbrance calculations

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v8.0.0...v8.1.0)

### Stories
* [MODFISTO-274](https://issues.folio.org/browse/MODFISTO-274) Extend transaction schema with voidedAmount field
* [MODFISTO-273](https://issues.folio.org/browse/MODFISTO-273) Voided transactions will no longer count towards budgets and budget totals
* [MODFISTO-269](https://issues.folio.org/browse/MODFISTO-269) Allow user to base encumbrance on "Initial encumbrance" during rollover

### Bug Fixes
* [MODFISTO-284](https://issues.folio.org/browse/MODFISTO-284) Cancel invoice does not update the encumbrance correctly
* [MODFISTO-283](https://issues.folio.org/browse/MODFISTO-283) Over encumbrance calculation not aligned with encumbrance limit calculation


## 8.0.0 - Released
The primary focus of this release was to update Finance schema for combination of Fund code and Expense class,
add validation for special character to Fund Code and Expense class code and prepare migration script

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v7.1.0...v8.0.0)

### Stories
* [MODFISTO-244](https://issues.folio.org/browse/MODFISTO-244) Add validation for special character to Fund Code and Expense class code and prepare migration script
* [MODFISTO-243](https://issues.folio.org/browse/MODFISTO-243) Define DTO schema for combination of fund code and expense class

### Bug Fixes
* [MODFISTO-247](https://issues.folio.org/browse/MODFISTO-247) Rollover errors when at least 1 order has encumbrance with initialAllocation = 0


## 7.1.0 - Released
This release contains new logic of financial processing, RMB update up to v33.0.0, personal data disclosure form added

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v7.0.3...v7.1.0)

### Stories
* [MODFISTO-240](https://issues.folio.org/browse/MODFISTO-240) It should be possible to unrelease expended encumbrance
* [MODFISTO-233](https://issues.folio.org/browse/MODFISTO-233) reOpen logic for encumbrances
* [MODFISTO-231](https://issues.folio.org/browse/MODFISTO-231) Issue with database migration for Iris release
* [MODFISTO-227](https://issues.folio.org/browse/MODFISTO-227) mod-finance-storage: Update RMB
* [MODFISTO-201](https://issues.folio.org/browse/MODFISTO-201) Add personal data disclosure form

### Bug Fixes
* [MODFISTO-237](https://issues.folio.org/browse/MODFISTO-237) Transaction table upgrade failure when purchase order record is missing
* [MODFISTO-225](https://issues.folio.org/browse/MODFISTO-225)	The lost/extra penny when creating encumbrances during rollover
* [MODFISTO-215](https://issues.folio.org/browse/MODFISTO-215) Remove uuid_generate_v4(), it fails in pgpool replication


## 7.0.3 - Released
The primary focus of this release was to fix cross migration fail once order is missing

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v7.0.2...v7.0.3)

### Hot Fixes
* [MODFISTO-237](https://issues.folio.org/browse/MODFISTO-237) Transaction table upgrade failure when purchase order record is missing

## 7.0.2 - Released
The primary focus of this release was to fix Unable to complete fiscal year rollover issue

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v7.0.1...v7.0.2)

### Bug Fixes
* [MODFISTO-234](https://issues.folio.org/browse/MODFISTO-234) Unable to complete fiscal year rollover

## 7.0.1 - Released
The primary focus of this release was to fix migration issues and lost/extra penny when creating encumbrances during rollover

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v7.0.0...v7.0.1)

### Bug Fixes

* [MODFISTO-225](https://issues.folio.org/browse/MODFISTO-225)	The lost/extra penny when creating encumbrances during rollover
* [MODFISTO-213](https://issues.folio.org/browse/MODFISTO-213)	ledgerfy does not exist when migating from v4.2.2 to v6.0.1

## 7.0.0 - Released
The primary focus of this release was to update RMB, support of the fiscal year rollover feature and update total amounts calculations.
Also a set of bugs were fixed.

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v6.0.1...v7.0.0)

### Technical tasks
* [MODFISTO-199](https://issues.folio.org/browse/MODFISTO-199) mod-finance-storage: Update RMB
* [MODFISTO-198](https://issues.folio.org/browse/MODFISTO-198) Fix fund type collection example JSON (acq-models)

### Stories
* [MODFISTO-224](https://issues.folio.org/browse/MODFISTO-224) Calculate transaction amount based on fundDistribution percentage value
* [MODFISTO-222](https://issues.folio.org/browse/MODFISTO-222) Budget created for inactive fund
* [MODFISTO-219](https://issues.folio.org/browse/MODFISTO-219) Allow user to delete a budget that has ONLY allocation type transactions
* [MODFISTO-216](https://issues.folio.org/browse/MODFISTO-216) Ensure Increase and decrease in allocation are always positive values
* [MODFISTO-210](https://issues.folio.org/browse/MODFISTO-210) Allow user to intentionally reset budget allowances during rollover
* [MODFISTO-209](https://issues.folio.org/browse/MODFISTO-209) Update test data to include an FY2021
* [MODFISTO-206](https://issues.folio.org/browse/MODFISTO-206) Ensure that Allowable Encumbrance and Allowable Expenditure restrictions are based on Total Funding
* [MODFISTO-204](https://issues.folio.org/browse/MODFISTO-204) Define and implement rollover error report storage API
* [MODFISTO-203](https://issues.folio.org/browse/MODFISTO-203) Update group-fiscal-year schema with new Financial information
* [MODFISTO-197](https://issues.folio.org/browse/MODFISTO-197) Adjust fiscal year schema with financial summary model	Max Shtanko
* [MODFISTO-195](https://issues.folio.org/browse/MODFISTO-195) Create view with Budget financial detail information
* [MODFISTO-194](https://issues.folio.org/browse/MODFISTO-194) Add order status to encumbrance schema
* [MODFISTO-192](https://issues.folio.org/browse/MODFISTO-192) Define Rollover transfer transactionType
* [MODFISTO-190](https://issues.folio.org/browse/MODFISTO-190) Migration : Fill new fields of the Budget schema with new Financial information
* [MODFISTO-188](https://issues.folio.org/browse/MODFISTO-188) Update Ledger schema with new Financial information
* [MODFISTO-187](https://issues.folio.org/browse/MODFISTO-187) Update logic with financial detail in the budget summary to improve the users ability
* [MODFISTO-186](https://issues.folio.org/browse/MODFISTO-186) Update Budget schema with new Financial information
* [MODFISTO-181](https://issues.folio.org/browse/MODFISTO-181) Move allocated logic from trigger to java code
* [MODFISTO-177](https://issues.folio.org/browse/MODFISTO-177) Create migration script for setting in all encumbrances "reEncumber" = true
* [MODFISTO-175](https://issues.folio.org/browse/MODFISTO-175) Define model and schema for the errors of the ledger fiscal year rollover
* [MODFISTO-174](https://issues.folio.org/browse/MODFISTO-174) Define model and schema for the progress of the ledger fiscal year rollover
* [MODFISTO-168](https://issues.folio.org/browse/MODFISTO-168) Define storage API for retrieving rollover status
* [MODFISTO-167](https://issues.folio.org/browse/MODFISTO-167) Add ledger fiscal year rollover logic
* [MODFISTO-164](https://issues.folio.org/browse/MODFISTO-164) Define model and schema of the fiscal year rollover for Ledger
* [MODFISTO-163](https://issues.folio.org/browse/MODFISTO-163) Define storage API for the ledger fiscal year rollover

### Bug Fixes
* [MODFISTO-221](https://issues.folio.org/browse/MODFISTO-221) Rollover budget ignores expense classes when creating new budget
* [MODFISTO-220](https://issues.folio.org/browse/MODFISTO-220) Funds lose group assignments after fiscal year rollover
* [MODFISTO-217](https://issues.folio.org/browse/MODFISTO-217) When not checking Rollover or Available in roll settings, systems rolls all amounts
* [MODFISTO-211](https://issues.folio.org/browse/MODFISTO-211) Incorrect budget overExpended value

## 6.0.1 - Released
The primary focus of this release was to fix RMB and logging issues

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v6.0.0...v6.0.1)

### Technical tasks
* [MODFISTO-182](https://issues.folio.org/browse/MODFISTO-182)	Update RMB up to v31.1.5

### Bug Fixes
* [MODFISTO-183](https://issues.folio.org/browse/MODFISTO-183)	Awaiting payment value in the encumbrance not updated correctly
* [MODFISTO-178](https://issues.folio.org/browse/MODFISTO-178)	No logging in honeysuckle version


## 6.0.0 - Released
The primary focus of this release introduce shared allocations and net transfer for budgets.
Also **major versions of APIs** were changed for **finance-storage.ledgers**

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v5.0.2...v6.0.0)

### Stories
* [MODFISTO-165](https://issues.folio.org/browse/MODFISTO-165)	Field "expenseClassId" must be a part of unique-constraint
* [MODFISTO-142](https://issues.folio.org/browse/MODFISTO-142)	Restrict transfer money if budget doesn't have enough available price
* [MODFISTO-140](https://issues.folio.org/browse/MODFISTO-140)	Composite orders web API updates budget objects with invalid date updated values
* [MODFISTO-137](https://issues.folio.org/browse/MODFISTO-137)	Return helpful information when delete Budget with assigned Expense Classes
* [MODFISTO-132](https://issues.folio.org/browse/MODFISTO-132)	mod-finance-storage: Update RMB
* [MODFISTO-128](https://issues.folio.org/browse/MODFISTO-128)	Migrate mod-finance-storage to JDK 11
* [MODFISTO-124](https://issues.folio.org/browse/MODFISTO-124)	Return helpful information when store Expense Class with name/code which already exist
* [MODFISTO-122](https://issues.folio.org/browse/MODFISTO-122)	Remove ledgerFY totals calculation logic
* [MODFISTO-120](https://issues.folio.org/browse/MODFISTO-120)	Update ledger and ledgerFY schemas with new field "netTransfer"
* [MODFISTO-119](https://issues.folio.org/browse/MODFISTO-119)	Create migration script for filling "NetTransfer" field in the budget
* [MODFISTO-115](https://issues.folio.org/browse/MODFISTO-115)	Define and implement budget-expense-classes API
* [MODFISTO-105](https://issues.folio.org/browse/MODFISTO-105)	Define and Implement Storage API for expense classes
* [MODFISTO-104](https://issues.folio.org/browse/MODFISTO-104)	Create expense class schema
* [MODFISTO-103](https://issues.folio.org/browse/MODFISTO-103)	Add logic for calculating budget "netTransfer" field and store it in DB
* [MODFISTO-102](https://issues.folio.org/browse/MODFISTO-102)	Update budget schema with new field "netTransfers"
* [MODFISTO-94](https://issues.folio.org/browse/MODFISTO-94)	Migration script for performed transactions

### Bug Fixes
* [MODFISTO-166](https://issues.folio.org/browse/MODFISTO-166)	Wrong calculation upon Pending payment update
* [MODFISTO-136](https://issues.folio.org/browse/MODFISTO-136)	Awaiting payment value not updated in encumbrances when invoice is paid.
* [MODFISTO-113](https://issues.folio.org/browse/MODFISTO-113)	Incorrect calculation of the "overEncumbrance"

## 5.0.2 - Released
This is a bugfix release and contains fix that user able to pay order and invoice if budget have enough money

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v5.0.1...v5.0.2)

### Bug Fixes
* [MODFISTO-125](https://issues.folio.org/browse/MODFISTO-125) MSU: cannot approve and pay an Amazon invoice

## 5.0.1 - Released
This is a bugfix release and contains fixes for budget, ledger, index creation.
Also module was migrated on RMB 30.2.0

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v5.0.0...v5.0.1)

### Bug Fixes
* [MODFISTO-118](https://issues.folio.org/browse/MODFISTO-118) Budget is not updated if encumbrance status has not been changed
* [MODFISTO-117](https://issues.folio.org/browse/MODFISTO-117) Index not being created during migration from FameFlower-Goldenrod
* [MODFISTO-114](https://issues.folio.org/browse/MODFISTO-114) Ledger FY totals not updated after encumbrance update

## 5.0.0 - Released
The primary focus of this release was to implement "all or nothing" mechanism for Pending payment transaction type
and update PUT transactions api to allow Unopen orders

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v4.2.2...v5.0.0)

### Stories
* [MODFISTO-111](https://issues.folio.org/browse/MODFISTO-111) Final verification migration scripts before release Q2 2020
* [MODFISTO-110](https://issues.folio.org/browse/MODFISTO-110) mod-finance-storage: Update to RMB v30.0.2
* [MODFISTO-109](https://issues.folio.org/browse/MODFISTO-109) Add ability update encumbrances and number of encumbrances based on order_transaction_summary
* [MODFISTO-107](https://issues.folio.org/browse/MODFISTO-107) Cross-module data migration scripts for pending payments logic
* [MODFISTO-106](https://issues.folio.org/browse/MODFISTO-106) Data migration scripts for schema changes
* [MODFISTO-101](https://issues.folio.org/browse/MODFISTO-101) Return helpful and clear error code, when Group name already exist
* [MODFISTO-90](https://issues.folio.org/browse/MODFISTO-90) Support all-or-nothing operations for pending payments by invoice
* [MODFISTO-89](https://issues.folio.org/browse/MODFISTO-89) Transaction schema changes to support creation pending_payment upon invoice approval
* [MODFISTO-85](https://issues.folio.org/browse/MODFISTO-85) Update the transaction 'source' enum
* [MODFISTO-78](https://issues.folio.org/browse/MODFISTO-78) Add indexes for tables
* [MODFISTO-75](https://issues.folio.org/browse/MODFISTO-75) LedgerFY records for past years

### Bug Fixes
* [MODFISTO-112](https://issues.folio.org/browse/MODFISTO-112) Available amount drops to 0 when encumbering money

## 4.2.2 - Released
The primary focus of this release was to fix critical issue with performing "all or nothing" mechanism

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v4.2.1...v4.2.2)

### Bug Fixes
* [MODFISTO-99](https://issues.folio.org/browse/MODFISTO-99) "All or nothing mechanism" performs two times

## 4.2.1 - Released
The primary focus of this release was to restrict processing transactions after performing "all or nothing" mechanism.

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v4.2.0...v4.2.1)

### Bug Fixes
* [MODFISTO-98](https://issues.folio.org/browse/MODFISTO-98) Impossible to delete/enable modules after running API tests
* [MODFISTO-97](https://issues.folio.org/browse/MODFISTO-97)  Issues upgrading module from Edelweiss to Fameflower
* [MODFISTO-96](https://issues.folio.org/browse/MODFISTO-96) Add logic upon budget deletion
* [MODFISTO-95](https://issues.folio.org/browse/MODFISTO-95) Not able to pay invoice after approval
* [MODFISTO-87](https://issues.folio.org/browse/MODFISTO-87) Able to apply transactions after all or nothing operation was performed
* [MODFISTO-84](https://issues.folio.org/browse/MODFISTO-84) Remove default values for budget.allowableEncumbrance and budget.allowableExpenditure

## 4.2.0 - Released
The primary focus of this release was to implement calculations and restrictions based on finance data

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v4.1.1...v4.2.0)

### Stories
* [MODFISTO-86](https://issues.folio.org/browse/MODFISTO-86) Update encumbrance doesn't work as expected
* [MODFISTO-83](https://issues.folio.org/browse/MODFISTO-83) Migration script improvement to support transition of non-unique index to unique one
* [MODFISTO-82](https://issues.folio.org/browse/MODFISTO-82) AwaitingPayment should never go below 0
* [MODFISTO-81](https://issues.folio.org/browse/MODFISTO-81) Budget expenditures must always be updated on payments/credits
* [MODFISTO-80](https://issues.folio.org/browse/MODFISTO-80) LedgerFY records must be updated on payments/credits
* [MODFISTO-63](https://issues.folio.org/browse/MODFISTO-63) Encumbrance restrictions
* [MODFISTO-62](https://issues.folio.org/browse/MODFISTO-62) Payment restrictions
* [MODFISTO-61](https://issues.folio.org/browse/MODFISTO-61) Budget/ledger unavailable shouldn't include overEncumbered amounts
* [MODFISTO-28](https://issues.folio.org/browse/MODFISTO-28) Transaction Calculations - Payments/Credits

### Bug Fixes
* [MODFISTO-91](https://issues.folio.org/browse/MODFISTO-91) Budgets aren't updated correctly upon updating/releasing encumbrance


## 4.1.1 - Released
This is a bugfix release, incrementing the fromModuleVersion so that updates to transactions.sql are captured

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v4.1.0...v4.1.1)

## 4.1.0 - Released
This is a bugfix release, with fixes to sample data for new year FY20 and ledger schema changes

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v4.0.1...v4.1.0)

### Stories
* [MODFISTO-64](https://issues.folio.org/browse/MODFISTO-64)  Ability to override allowableEncumbrance/allowableExpenditure

### Bug Fixes
* [MODFISTO-74](https://issues.folio.org/browse/MODFISTO-74)  mod-finance storage fails tenant init when loadSample is enabled

## 4.0.1 - Released
The primary focus of this release was to fix logic for the finance calculated values

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v4.0.0...v4.0.1)

### Stories
* [MODFISTO-67](https://issues.folio.org/browse/MODFISTO-67)  Update RMB to 29.1.1

### Bug Fixes
* [MODFISTO-72](https://issues.folio.org/browse/MODFISTO-72)  ledgerFY.unavailable and budget.unavailable must not change during Transfer
* [MODFISTO-71](https://issues.folio.org/browse/MODFISTO-71)  Persist totals to ledgerFY table
* [MODFISTO-68](https://issues.folio.org/browse/MODFISTO-68)  Fix Transaction Calculations - Encumbrance upon update


## 4.0.0 - Released
The primary focus of this release was to implement additional finance API and significantly update existent finance schemas.

[Full Changelog](https://github.com/folio-org/mod-finance-storage/compare/v3.0.0...v4.0.0)

### Stories
* [MODFISTO-59](https://issues.folio.org/browse/MODFISTO-59)  Transaction Calculations - Encumbrance upon update
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
* [MODFISTO-2358](https://issues.folio.org/browse/FOLIO-2358)  Use JVM features (UseContainerSupport, MaxRAMPercentage) to manage container memory

### Bug Fixes
* [MODFISTO-52](https://issues.folio.org/browse/MODFISTO-52)  Unavailable increasing when allocating from budget to budget

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
