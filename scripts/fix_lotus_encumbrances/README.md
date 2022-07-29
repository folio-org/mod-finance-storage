# Fix Lotus Encumbrances

This script will fix several issues in encumbrances related to the fiscal year rollover for FOLIO Lotus.
In particular, after a fiscal year rollover in Lotus, there can be a mismatch between budget encumbrances and purchase order encumbrances.

## Running the script :

- ***Make a backup first !***
- Expect the script to take a long time with a large number of orders.
- Operations affecting order encumbrances or budgets should be avoided while the script is running.
- The script should not be used before a rollover in Lotus (because if it was, encumbrances would not be created for closed orders, and they could not be reopened - see [MODORDERS-706](https://issues.folio.org/browse/MODORDERS-706)). It can be used after a rollover for all ledgers, or just before migration to Morning Glory. If necessary it could also be used in Morning Glory.

### Script arguments :

- Fiscal year code
- Okapi URL
- Tenant
- Username
- User password is required as a command-line input.

### Execution example :
`./fix_lotus_encumbrances.py 'FY2022' 'http://localhost:9130/' 'diku' 'diku_admin'`

### Required permissions for the user
These can be set with a permission group created with the API.

- `finance.budgets.collection.get`
- `finance.encumbrances.item.put`
- `finance.fiscal-years.collection.get`
- `finance.order-transaction-summaries.item.put`
- `finance.transactions.collection.get`
- `finance.transactions.item.get`
- `finance-storage.budgets.item.put`
- `orders.collection.get`

## Script Logic

- Fix the `orderStatus` property of encumbrances for closed orders. In order to do this, the encumbrances have to be unreleased first and released afterwards because it is not possible to change this property for released encumbrances.
- Change the encumbrance status to `Unreleased` for all open orders' encumbrances with non-zero amounts
- Recalculate the encumbered property for all the budgets related to these encumbrances by summing the related unreleased encumbrances.
- Release all unreleased encumbrances for closed orders

## JIRA ticket
[MODFISTO-326](https://issues.folio.org/browse/MODFISTO-326) - Create a script to fix Lotus encumbrance issues

