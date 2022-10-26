#!/usr/bin/env python
import asyncio
import getpass
import json
import sys
import time
from decimal import Decimal
from http import HTTPStatus

import httpx
import requests

ITEM_MAX = 2147483647

okapi_url = ''
headers = {}
client = httpx.AsyncClient()

# request timeout in seconds
ASYNC_CLIENT_TIMEOUT = 30

# limit the number of parallel threads.
# Try different values. Bigger values - for increasing performance, but could produce "Connection timeout exception"
MAX_ACTIVE_THREADS = 7


# ---------------------------------------------------
# Utility functions

def raise_exception_for_reply(r):
    raise Exception('Status code: {}. Response: "{}"'.format(r.status_code, r.text))


def login(tenant, username, password):
    login_headers = {'x-okapi-tenant': tenant, 'Content-Type': 'application/json'}
    data = {'username': username, 'password': password}
    try:
        r = requests.post(okapi_url + 'authn/login', headers=login_headers, json=data)
        if r.status_code != 201:
            raise_exception_for_reply(r)
        print('Logged in successfully.')
        okapi_token = r.json()['okapiToken']
        return {'x-okapi-tenant': tenant, 'x-okapi-token': okapi_token, 'Content-Type': 'application/json'}
    except Exception as err:
        print('Error during login:', err)
        raise SystemExit(1)


async def get_request(url: str, query: str) -> httpx.Response:
    params = {'query': query, 'offset': '0', 'limit': ITEM_MAX}

    try:
        resp = await client.get(url, headers=headers, params=params, timeout=ASYNC_CLIENT_TIMEOUT)

        if resp.status_code == HTTPStatus.OK:
            return resp.json()
        else:
            print('Error getting records by {} ?query= "{}": \n{} '.format(url, query, resp.text))
            raise SystemExit(1)
    except Exception as err:
        print(f'Error getting records by {url}?query={query}: {err=}')
        raise SystemExit(1)


async def post_request(url: str, data) -> httpx.Response:
    try:
        resp = await client.post(url, headers=headers, content='', timeout=ASYNC_CLIENT_TIMEOUT)
        if resp.status_code == HTTPStatus.OK:
            return resp
        else:
            print('Error creating record {} \n "{}": \n{} '.format(url, data, resp.text))
            raise SystemExit(1)
    except Exception as err:
        print(f'Error creating record {url} "{data}": {err=}')
        raise SystemExit(1)


async def put_request(url: str, data) -> httpx.Response:
    try:
        resp = await client.put(url, headers=headers, data=json.dumps(data), timeout=ASYNC_CLIENT_TIMEOUT)
        if resp.status_code == HTTPStatus.NO_CONTENT:
            return resp
        else:
            print('Error updating record {} "{}": {}'.format(url, data, resp.text))
            raise SystemExit(1)

    except Exception as err:
        print(f'Error updating record {url} "{data}": {err=}')
        raise SystemExit(1)


async def put_request_with_semaphore(url: str, data, sem) -> httpx.Response:
    try:
        resp = await client.put(url, headers=headers, data=json.dumps(data))
        sem.release()
        if resp.status_code == HTTPStatus.NO_CONTENT:
            return resp
        else:
            print('Error updating record {} "{}": {}'.format(url, data, resp.text))
            raise SystemExit(1)

    except Exception as err:
        print(f'Error updating record {url} "{data}": {err=}')
        raise SystemExit(1)


def get_fiscal_year_ids_by_query(query):
    params = {'query': query, 'offset': '0', 'limit': ITEM_MAX}
    try:
        r = requests.get(okapi_url + 'finance-storage/fiscal-years', headers=headers, params=params)
        if r.status_code != 200:
            raise_exception_for_reply(r)
        fiscal_years = r.json()['fiscalYears']
        ids = []
        for fiscal_year in fiscal_years:
            ids.append(fiscal_year.get('id'))
    except Exception as err:
        print('Error getting fiscal year ids with query "{}": {}'.format(query, err))
        raise SystemExit(1)
    return ids


def get_order_ids_by_query(query):
    params = {'query': query, 'offset': '0', 'limit': ITEM_MAX}
    try:
        r = requests.get(okapi_url + 'orders-storage/purchase-orders', headers=headers, params=params)
        if r.status_code != 200:
            raise_exception_for_reply(r)
        orders = r.json()['purchaseOrders']
        ids = []
        for order in orders:
            ids.append(order.get('id'))
    except Exception as err:
        print('Error getting order ids with query "{}": {}'.format(query, err))
        raise SystemExit(1)
    return ids


async def get_encumbrances_by_query(query):
    url = okapi_url + 'finance-storage/transactions'
    response = await get_request(url, query)
    return response['transactions']


async def get_encumbrance_by_ids(encumbrance_ids):
    query = ''
    for idx, enc_id in enumerate(encumbrance_ids):
        if len(encumbrance_ids) != idx + 1:
            query = query + "id=={} OR ".format(enc_id)
        else:
            query = query + "id=={}".format(enc_id)
    resp = await get_request(okapi_url + 'finance-storage/transactions', query)

    return resp['transactions']


def get_budgets_by_query(query):
    params = {'query': query, 'offset': '0', 'limit': ITEM_MAX}
    try:
        r = requests.get(okapi_url + 'finance-storage/budgets', headers=headers, params=params)
        if r.status_code != 200:
            raise_exception_for_reply(r)
        budgets = r.json()['budgets']
    except Exception as err:
        print('Error getting budgets with query "{}": {}'.format(query, err))
        raise SystemExit(1)
    return budgets


def get_fiscal_year_id(fiscal_year_code):
    query = 'code=="{}"'.format(fiscal_year_code)
    fiscal_year_ids = get_fiscal_year_ids_by_query(query)
    if len(fiscal_year_ids) == 0:
        print('Could not find fiscal year "{}".'.format(fiscal_year_code))
        raise SystemExit(1)
    return fiscal_year_ids[0]


def get_closed_orders_ids():
    query = 'workflowStatus=="Closed"'
    closed_orders_ids = get_order_ids_by_query(query)
    print('Closed orders:', len(closed_orders_ids))
    return closed_orders_ids


def get_open_orders_ids():
    query = 'workflowStatus=="Open"'
    open_orders_ids = get_order_ids_by_query(query)
    print('  Open orders:', len(open_orders_ids))
    return open_orders_ids


def build_order_by_fund_ids_query(fund_ids):
    fund_ids_query = ''
    if len(fund_ids) > 0:
        fund_ids_query = ' AND ('

        for idx, fund_id in enumerate(fund_ids):
            fund_ids_query += 'poLine.fundDistribution = /@fundId ="{}"'.format(fund_id)
            if len(fund_ids) != (idx + 1):
                fund_ids_query += ' OR '

        fund_ids_query += ')'
    return fund_ids_query


def build_polines_by_fund_ids_query(fund_ids):
    fund_ids_query = ''
    if len(fund_ids) > 0:
        fund_ids_query = ' AND ('
        for idx, fund_id in enumerate(fund_ids):
            fund_ids_query += 'fundDistribution = /@fundId ="{}"'.format(fund_id)
            if len(fund_ids) != (idx + 1):
                fund_ids_query += ' OR '

        fund_ids_query += ')'
    return fund_ids_query


async def put_encumbrance(encumbrance):
    try:
        url = '{}finance-storage/transactions/{}'.format(okapi_url, encumbrance['id'])
        r = requests.put(url, headers=headers, json=encumbrance)
        if r.status_code != 204:
            raise_exception_for_reply(r)
    except Exception as err:
        print(f'Error putting encumbrance "{encumbrance}": {err=}')
        raise SystemExit(1)


async def transaction_summary(order_id, num_transactions):
    data = {'id': order_id, 'numTransactions': num_transactions}
    url = '{}finance-storage/order-transaction-summaries/{}'.format(okapi_url, order_id)

    return await put_request(url, data)


async def get_budget_by_fund_id(fund_id, fiscal_year_id):
    query = 'fundId=={} AND fiscalYearId=={}'.format(fund_id, fiscal_year_id)
    budgets = await get_request(okapi_url + 'finance/budgets', query)
    if len(budgets['budgets']) == 0:
        print('Could not find budget for fund "{}" and fiscal year "{}".'.format(fund_id, fiscal_year_id))
        raise SystemExit(1)
    return budgets['budgets'][0]


def put_budget(budget):
    # NOTE: readonly properties will be sent too, curiously they are normally saved to storage when the budget is
    # modified with the UI too. Values in storage might be inconsistent when encumbered is changed,
    # but they get recalculated on a GET.
    try:
        url = '{}finance-storage/budgets/{}'.format(okapi_url, budget['id'])
        r = requests.put(url, headers=headers, json=budget)
        if r.status_code != 204:
            raise_exception_for_reply(r)
    except Exception as err:
        print('Error putting budget "{}": {}'.format(budget, err))
        raise SystemExit(1)


async def get_order_encumbrances(order_id, fiscal_year_id, sem):
    url = okapi_url + 'finance-storage/transactions'
    query = 'encumbrance.sourcePurchaseOrderId=={} AND fiscalYearId=={}'.format(order_id, fiscal_year_id)
    response = await get_request(url, query)
    sem.release()
    return response['transactions']


# ---------------------------------------------------
# Fix encumbrance orderStatus for closed orders

async def get_order_encumbrances_to_fix(order_id, fiscal_year_id):
    query = 'encumbrance.orderStatus<>"Closed" AND encumbrance.sourcePurchaseOrderId=={} AND fiscalYearId=={}' \
        .format(order_id, fiscal_year_id)
    url = okapi_url + 'finance-storage/transactions'

    return await get_request(url, query)


async def unrelease_order_encumbrances(order_id, encumbrances):
    await transaction_summary(order_id, len(encumbrances))

    enc_futures = []
    for encumbrance in encumbrances:
        encumbrance['encumbrance']['status'] = 'Unreleased'
        url = '{}finance-storage/transactions/{}'.format(okapi_url, encumbrance['id'])
        enc_futures.append(asyncio.ensure_future(put_request(url, encumbrance)))
    await asyncio.gather(*enc_futures)

    # the encumbrance amounts get modified (and the version in MG), so we need to get a fresh version
    modified_encumbrance_futures = []

    # split retrieving encumbrances by small id lists
    # reasons:
    # - retrieving one by one will slow down performance
    # - retrieving all at once will generate too long query and fail the request due to RMB restrictions
    enc_ids = build_ids_2d_array(encumbrances)
    for enc_ids_row in enc_ids:
        modified_encumbrance_futures.append(get_encumbrance_by_ids(enc_ids_row))
    modified_encumbrances = await asyncio.gather(*modified_encumbrance_futures)

    flatten_list_of_modified_encumbrances = sum(modified_encumbrances, [])
    return flatten_list_of_modified_encumbrances


def build_ids_2d_array(entities):
    # prepare two-dimensional array of ids for requesting the records by ids in bulks
    ids_2d_array = []
    index = 0
    for row in range(ITEM_MAX):
        inner_list = []
        for col in range(20):
            if index == len(entities):
                break
            inner_list.append(entities[index]['id'])
            index = index + 1
        ids_2d_array.append(inner_list)
        if index == len(entities):
            break
    return ids_2d_array


async def fix_order_status_and_release_encumbrances(order_id, encumbrances):
    try:
        await transaction_summary(order_id, len(encumbrances))

        enc_futures = []
        for encumbrance in encumbrances:
            encumbrance['encumbrance']['status'] = 'Released'
            encumbrance['encumbrance']['orderStatus'] = 'Closed'
            url = '{}finance-storage/encumbrances/{}'.format(okapi_url, encumbrance['id'])
            enc_futures.append(asyncio.ensure_future(put_request(url, encumbrance)))

    except Exception as err:
        print('Error when fixing order status in encumbrances for order {}:'.format(order_id), err)
        raise SystemExit(1)
    return await asyncio.gather(*enc_futures)


async def fix_order_encumbrances_order_status(order_id, encumbrances):
    # We can't just PUT the encumbrance with a modified orderStatus, because
    # mod-finance-storage's EncumbranceService ignores changes to released encumbrances
    # unless it's to unrelease them. So we have to unrelease the encumbrances first.
    try:
        print('  Fixing {} encumbrance(s) for order {}...'.format(len(encumbrances), order_id))
        modified_encumbrances = await unrelease_order_encumbrances(order_id, encumbrances)
        if len(modified_encumbrances) != 0:
            await fix_order_status_and_release_encumbrances(order_id, modified_encumbrances)
    except Exception as err:
        print('Error when fixing order status in encumbrances for order {}:'.format(order_id), err)
        raise SystemExit(1)


async def fix_encumbrance_order_status_for_closed_order(order_id, fiscal_year_id, sem):
    encumbrances = await get_order_encumbrances_to_fix(order_id, fiscal_year_id)
    if len(encumbrances['transactions']) != 0:
        await fix_order_encumbrances_order_status(order_id, encumbrances['transactions'])
    sem.release()
    return len(encumbrances['transactions'])


async def fix_encumbrance_order_status_for_closed_orders(closed_orders_ids, fiscal_year_id):
    print('Fixing encumbrance order status for closed orders...')
    fix_encumbrance_futures = []
    max_active_order_threads = 5
    sem = asyncio.Semaphore(max_active_order_threads)
    for order_id in closed_orders_ids:
        await sem.acquire()
        fixed_encumbrance_future = asyncio.ensure_future(fix_encumbrance_order_status_for_closed_order(order_id, fiscal_year_id, sem))
        fix_encumbrance_futures.append(fixed_encumbrance_future)
    nb_fixed_encumbrances = await asyncio.gather(*fix_encumbrance_futures)

    print('  Fixed order status for {} encumbrances.'.format(sum(nb_fixed_encumbrances)))


# ---------------------------------------------------
# Unrelease open orders encumbrances with non-zero amounts
async def unrelease_encumbrances_with_non_zero_amounts(order_id, fiscal_year_id, sem):
    query = 'amount<>0.0 AND encumbrance.status=="Released" AND encumbrance.sourcePurchaseOrderId=={} AND fiscalYearId=={}'.format(
        order_id, fiscal_year_id)
    transactions_response = await get_request(okapi_url + 'finance-storage/transactions', query)

    order_encumbrances = transactions_response['transactions']
    # unrelease encumbrances by order id
    if len(order_encumbrances) != 0:
        await unrelease_encumbrances(order_id, order_encumbrances)

    sem.release()
    return len(order_encumbrances)


async def unrelease_encumbrances(order_id, encumbrances):
    print('  Unreleasing {} encumbrances for the order {}'.format(len(encumbrances), order_id))

    await transaction_summary(order_id, len(encumbrances))
    enc_futures = []

    sem = asyncio.Semaphore(MAX_ACTIVE_THREADS)
    for encumbrance in encumbrances:
        await sem.acquire()
        encumbrance['encumbrance']['status'] = 'Unreleased'
        url = '{}finance-storage/encumbrances/{}'.format(okapi_url, encumbrance['id'])
        enc_futures.append(asyncio.ensure_future(put_request_with_semaphore(url, encumbrance, sem)))

    await asyncio.gather(*enc_futures)


async def unrelease_open_orders_encumbrances_with_nonzero_amounts(fiscal_year_id, open_orders_ids):
    print('Unreleasing open orders encumbrances with non-zero amounts...')
    enc_futures = []
    sem = asyncio.Semaphore(MAX_ACTIVE_THREADS)
    for order_id in open_orders_ids:
        await sem.acquire()
        enc_futures.append(asyncio.ensure_future(unrelease_encumbrances_with_non_zero_amounts(order_id, fiscal_year_id, sem)))
    unreleased_encumbrances_amounts = await asyncio.gather(*enc_futures)

    print(
        '  Unreleased {} open orders encumbrances with non-zero amounts.'.format(sum(unreleased_encumbrances_amounts)))


# ---------------------------------------------------
# Recalculate budget encumbered

async def recalculate_budget_encumbered(open_and_closed_orders_ids, fiscal_year_id):
    # Recalculate the encumbered property for all the budgets related to these encumbrances
    # Take closed orders into account because we might have to set a budget encumbered to 0
    print('Recalculating budget encumbered for {} orders ...'.format(len(open_and_closed_orders_ids)))
    encumbered_for_fund = {}
    enc_future = []
    sem = asyncio.Semaphore(MAX_ACTIVE_THREADS)
    for order_id in open_and_closed_orders_ids:
        await sem.acquire()
        enc_future.append(asyncio.ensure_future(get_order_encumbrances(order_id, fiscal_year_id, sem)))

    encumbrances = sum(await asyncio.gather(*enc_future), [])

    for encumbrance in encumbrances:
        fund_id = encumbrance['fromFundId']
        if fund_id not in encumbered_for_fund:
            encumbered_for_fund[fund_id] = 0
        else:
            encumbered_for_fund[fund_id] += Decimal(str(encumbrance['amount']))
    print('  Updating budgets...')

    update_budget_futures = []
    for fund_id, encumbered in encumbered_for_fund.items():
        await sem.acquire()
        update_budget_futures.append(asyncio.ensure_future(update_budgets(str(encumbered), fund_id, fiscal_year_id, sem)))
    nb_modified = sum(await asyncio.gather(*update_budget_futures))

    print('  Edited {} budget(s).'.format(nb_modified))
    print('  Done recalculating budget encumbered.')


async def update_budgets(encumbered, fund_id, fiscal_year_id, sem):
    nb_modified = 0
    budget = await get_budget_by_fund_id(fund_id, fiscal_year_id)

    # Cast into decimal values, so 0 == 0.0 == 0.00 will return true
    if Decimal(str(budget['encumbered'])) != Decimal(encumbered):
        print('    Budget "{}": changing encumbered from {} to {}'.format(budget['name'], budget['encumbered'],
                                                                          encumbered))
        budget['encumbered'] = encumbered

        url = '{}finance-storage/budgets/{}'.format(okapi_url, budget['id'])
        await put_request(url, budget)
        nb_modified = 1
    sem.release()
    return nb_modified


# ---------------------------------------------------
# Release unreleased encumbrances for closed orders
async def release_encumbrances(order_id, encumbrances):
    print('  Releasing {} encumbrances for the order {}'.format(len(encumbrances), order_id))

    await transaction_summary(order_id, len(encumbrances))
    enc_futures = []
    for encumbrance in encumbrances:
        encumbrance['encumbrance']['status'] = 'Released'
        url = '{}finance-storage/encumbrances/{}'.format(okapi_url, encumbrance['id'])
        enc_futures.append(asyncio.ensure_future(put_request(url, encumbrance)))
    await asyncio.gather(*enc_futures)
    return len(enc_futures)


async def get_order_encumbrances_to_release(order_id, fiscal_year_id):
    query = 'encumbrance.status=="Unreleased" AND encumbrance.sourcePurchaseOrderId=={} AND fiscalYearId=={}' \
        .format(order_id, fiscal_year_id)
    return await get_encumbrances_by_query(query)


async def release_order_encumbrances(order_id, fiscal_year_id, sem):
    encumbrances = await get_order_encumbrances_to_release(order_id, fiscal_year_id)
    if len(encumbrances) != 0:
        await release_encumbrances(order_id, encumbrances)
    sem.release()
    return len(encumbrances)


async def release_unreleased_encumbrances_for_closed_orders(closed_orders_ids, fiscal_year_id):
    print('Releasing unreleased encumbrances for closed orders...')
    nb_released_encumbrance_futures = []
    sem = asyncio.Semaphore(MAX_ACTIVE_THREADS)

    for order_id in closed_orders_ids:
        await sem.acquire()
        nb_released_encumbrance_futures.append(asyncio.ensure_future(release_order_encumbrances(order_id, fiscal_year_id, sem)))
    nb_released_encumbrances = await asyncio.gather(*nb_released_encumbrance_futures)

    print('  Released {} encumbrances.'.format(sum(nb_released_encumbrances)))


def get_funds():
    params = {'offset': '0', 'limit': ITEM_MAX}
    try:
        r = requests.get(okapi_url + 'finance-storage/funds', headers=headers, params=params)
        if r.status_code != 200:
            raise_exception_for_reply(r)
        funds = r.json()['funds']
    except Exception as err:
        print('Error getting order ids with query "{}": {}'.format(query, err))
        raise SystemExit(1)
    return funds


async def get_polines_by_order_id(order_id):
    query = 'purchaseOrderId=={}'.format(order_id)
    resp = await get_request(okapi_url + 'orders-storage/po-lines', query)
    po_lines = resp['poLines']
    return po_lines


def lookup_fund(poline_id, code, all_funds):
    for fund in all_funds:
        if fund['code'] == code:
            return fund['id']
    print('Fund code {} from poline {} not exists. Correct the data and rerun the script'.format(code, poline_id))
    raise SystemExit(1)


async def process_order_encumbrances_relations(order_id, fiscal_year_id, all_funds, order_sem):
    po_lines = await get_polines_by_order_id(order_id)

    poline_futures = []
    if len(po_lines) > 0:
        for po_line in po_lines:
            poline_futures.append(asyncio.ensure_future(process_po_line_encumbrances_relations(po_line, fiscal_year_id, all_funds)))
        await asyncio.gather(*poline_futures)

    order_sem.release()


async def check_if_fd_needs_updates(poline_id, fd, query):
    encumbrances = await get_encumbrances_by_query(query)
    if len(encumbrances) > 0 and encumbrances[0]['id'] != fd['encumbrance']:
        print('Updating poline {} encumbrance {} with new value {}'.format(poline_id, fd['encumbrance'], encumbrances[0]['id']))
        fd['encumbrance'] = encumbrances[0]['id']
        return True
    return False


async def process_po_line_encumbrances_relations(poline, fiscal_year_id, all_funds):
    enc_futures = []
    for fd in poline['fundDistribution']:
        if fd.get('encumbrance') is not None:
            query = 'amount<>0.0 AND encumbrance.sourcePoLineId=={} AND fiscalYearId<>{} AND fromFundId=={}'.format(
                poline['purchaseOrderId'], fiscal_year_id, fd.get('fundId'))
            enc_futures.append(asyncio.ensure_future(check_if_fd_needs_updates(poline['id'], fd, query)))
    poline_needs_updates = await asyncio.gather(*enc_futures)

    # update poline if one or more fund distributions modified
    if True in poline_needs_updates:
        url = '{}orders-storage/po-lines/{}'.format(okapi_url, poline.get('id'))
        await put_request(url, poline)


# Get po_lines by order id
    # for each poline process fund distributions
        # for each fund distribution check encumbrance relationship and modify if needed -
        # in case if encumbrance id specified in fund distribution:
        # get encumbrance by poline id and current FY<>transaction.FY and amount <> 0
        # if fd.encumbrance != transaction.id --> set new encumbrance reference
        # update poline if modified
async def fix_poline_encumbrances_relations(open_orders_ids, fiscal_year_id, all_funds):
    print('Fixing poline-encumbrance links...')
    orders_futures = []
    order_sem = asyncio.Semaphore(MAX_ACTIVE_THREADS)
    for idx, order_id in enumerate(open_orders_ids):
        await order_sem.acquire()
        orders_futures.append(asyncio.ensure_future(process_order_encumbrances_relations(order_id, fiscal_year_id, all_funds, order_sem)))
        progress(idx, len(open_orders_ids), 'Fixing poline-encumbrance links')
    await asyncio.gather(*orders_futures)


def progress(index, total_elements, label=''):
    progress_length = 100
    current_progress_length = int(round(progress_length * index / float(total_elements)))

    percents_completed = round(100.0 * index / float(total_elements), 1)
    bar = '=' * current_progress_length + '-' * (progress_length - current_progress_length)

    sys.stdout.write('%s - [%s] %s%s \r' % (label, bar, percents_completed, '%'))
    sys.stdout.flush()


# ---------------------------------------------------
# Main
async def main():
    global okapi_url, headers
    if len(sys.argv) != 5:
        print("Syntax: ./fix_lotus_encumbrances.py 'fiscal_year_code' 'okapi_url' 'tenant' 'username'")
        raise SystemExit(1)
    fiscal_year_code = sys.argv[1]
    okapi_url = sys.argv[2]
    tenant = sys.argv[3]
    username = sys.argv[4]
    password = getpass.getpass('Password:')
    headers = login(tenant, username, password)
    initial_time = time.time()

    fiscal_year_id = get_fiscal_year_id(fiscal_year_code)

    all_funds = get_funds()

    # get open and closed order ids
    closed_orders_ids = get_closed_orders_ids()
    open_orders_ids = get_open_orders_ids()
    open_and_closed_orders_ids = closed_orders_ids + open_orders_ids

    await fix_poline_encumbrances_relations(open_orders_ids, fiscal_year_id, all_funds)

    await fix_encumbrance_order_status_for_closed_orders(closed_orders_ids, fiscal_year_id)

    await unrelease_open_orders_encumbrances_with_nonzero_amounts(fiscal_year_id, open_orders_ids)

    await recalculate_budget_encumbered(open_and_closed_orders_ids, fiscal_year_id)

    await release_unreleased_encumbrances_for_closed_orders(closed_orders_ids, fiscal_year_id)
    print('Time spent: {} {}'.format(round(time.time() - initial_time, 2), 'sec'))
    print('Done.')


loop = asyncio.get_event_loop()
loop.run_until_complete(main())
loop.close()
