# Fix Encumbrances

This script will fix several issues in encumbrances related to the fiscal year rollover for FOLIO Lotus, and other issues related to encumbrances in later FOLIO versions.
In particular, after a fiscal year rollover in Lotus, there can be a mismatch between budget encumbrances and purchase order encumbrances.

## Running the script :

- ***Make a backup first !*** (unless you plan to use dry-run mode)
- Use a recent version of python (at least 3.8)
- Install the required python packages if needed:\
  `pip install requests httpx`
- Expect the script to take a long time with a large number of orders.
- Operations affecting order encumbrances or budgets should be avoided while the budgets are recalculated.
- The script should not be used before a rollover in Lotus (because if it was, encumbrances would not be created for closed orders, and they could not be reopened - see [MODORDERS-706](https://folio-org.atlassian.net/browse/MODORDERS-706)).

### Version to use :
Because of a change in the transaction API, there is a new version for Quesnelia and later. This new version also has more features.
- Use `fix_encumbrances.py` in Quesnelia and later versions of FOLIO
- Use `old_fix_encumbrances.py` for any previous version

### Script arguments :

- Fiscal year code (use the current fiscal year when fixing issues with the fiscal year rollover)
- Okapi URL - use port `9130` for Okapi, `8000` for Eureka
- Tenant
- Username
- User password is required as a command-line input.

### Execution example :
`python3 ./fix_encumbrances.py 'FY2025' 'http://localhost:9130/' 'diku' 'diku_admin'`

To save output in a file, use `tee` on Linux:\
`python3 ./fix_encumbrances.py 'FY2025' 'http://localhost:9130/' 'diku' 'diku_admin' | tee my_latest_run.log`

### Required permissions for the user
These can be set with a permission group created with the API.

#### For Quesnelia and later:
- `finance-storage.funds.collection.get`
- `finance-storage.budgets.collection.get`
- `finance-storage.fiscal-years.collection.get`
- `finance-storage.transactions.collection.get`
- `finance-storage.transactions.batch.execute`
- `finance-storage.budgets.item.put`
- `orders-storage.purchase-orders.collection.get`
- `orders-storage.purchase-orders.item.get`
- `orders-storage.po-lines.collection.get`
- `orders-storage.po-lines.item.get`
- `orders-storage.po-lines.item.put`

#### For any older version:
- `finance-storage.funds.collection.get`
- `finance-storage.budgets.collection.get`
- `finance-storage.fiscal-years.collection.get`
- `finance-storage.order-transaction-summaries.item.put`
- `finance-storage.transactions.collection.get`
- `finance-storage.transactions.item.put`
- `finance-storage.transactions.item.delete`
- `finance-storage.budgets.item.put`
- `orders-storage.purchase-orders.collection.get`
- `orders-storage.po-lines.collection.get`
- `orders-storage.po-lines.item.get`
- `orders-storage.po-lines.item.put`

## Options
After the script is launched, it is possible to select a dry-run mode. This will execute the script in the same way as normal but without any actual modification. When running all fixes, it might execute differently because some operations depend on previous ones, such as when recalculating budget encumbrances.

After the mode selection, it is possible to select the operation(s) to execute:

- Run all fixes (can be long).
- Remove duplicate encumbrances (remove one when 2 encumbrances have the same `orderId`/`sourcePoLineId`/`fromFundId`/`expenseClassId`/`fiscalYearId`).
- Fix order line - encumbrance relations: fix `encumbrance` links in PO lines in case the poline fund distribution refers to the encumbrance from the previous fiscal year; also fix the fund id in encumbrances if they don't match their po line distribution fund id. This whole step is disabled for past fiscal years.
- Fix the `orderStatus` property of encumbrances for closed orders.
- Fix the `orderStatus`, `orderType` and `reEncumber` properties of encumbrances for open and pending orders.
- Remove pending order links to encumbrances in previous fiscal years (Quesnelia+ version only; current fiscal year only) - this resolves an issue when opening a pending order after FYRO. It only removes encumbrance links if they use a fund without an active budget.
- Change the encumbrance status to `Unreleased` for all open orders' encumbrances with non-zero amounts.
- Release open order unreleased encumbrances with negative amounts when they have `amountAwaitingPayment` or `amountExpended` > 0.
- Release cancelled order line encumbrances: releases encumbrances when their order line has a `paymentStatus` of `Cancelled`.
- Recalculate the encumbered property for all the budgets related to these encumbrances by summing the related unreleased encumbrances.
- Release all unreleased encumbrances for closed orders.

## JIRA tickets
- [MODFISTO-326](https://folio-org.atlassian.net/browse/MODFISTO-326) - Create a script to fix Lotus encumbrance issues
- [MODFISTO-329](https://folio-org.atlassian.net/browse/MODFISTO-329) - Migrate python script to the async approach
- [MODFISTO-337](https://folio-org.atlassian.net/browse/MODFISTO-337) - Script improvements
- [MODFISTO-350](https://folio-org.atlassian.net/browse/MODFISTO-350) - Fix POLs with links to encumbrances from previous fiscal years.
- [MODFISTO-367](https://folio-org.atlassian.net/browse/MODFISTO-367) - Avoid requesting too many orders at once.
- [MODFISTO-368](https://folio-org.atlassian.net/browse/MODFISTO-368) - Fix negative encumbrances.
- [MODFISTO-375](https://folio-org.atlassian.net/browse/MODFISTO-375) - Remove duplicate encumbrances
- [MODFISTO-382](https://folio-org.atlassian.net/browse/MODFISTO-382) - Interactive menu
- [MODFISTO-385](https://folio-org.atlassian.net/browse/MODFISTO-385) - Fix encumbrances' fromFundId
- [MODFISTO-419](https://folio-org.atlassian.net/browse/MODFISTO-419) - Add dry-run mode to FYRO script
- [MODFISTO-383](https://folio-org.atlassian.net/browse/MODFISTO-383) - Encumbrance script: release encumbrances for cancelled POLs
- [MODFISTO-425](https://folio-org.atlassian.net/browse/MODFISTO-425) - Disable fixing encumbrance fund id for past fiscal years
- [MODFISTO-462](https://folio-org.atlassian.net/browse/MODFISTO-462) - Update the encumbrance script to use the new transaction API
- [MODFISTO-514](https://folio-org.atlassian.net/browse/MODFISTO-514) - Remove links to encumbrances for pending orders in an old FY
- [MODFISTO-417](https://folio-org.atlassian.net/browse/MODFISTO-417) - Fix encumbrances with a bad orderType
- [MODFISTO-491](https://folio-org.atlassian.net/browse/MODFISTO-491) - Fix inconsistent reEncumber values
