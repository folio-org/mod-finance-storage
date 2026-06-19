#!/usr/bin/env python
import asyncio
import getpass
import json
import sys
import time
from datetime import datetime
from decimal import Decimal
from itertools import islice, chain
from http import HTTPStatus
from http.cookiejar import CookieJar, Cookie
from typing import Union

import httpx

ITEM_MAX = 2147483647
MAX_BY_CHUNK = 1000
IDS_CHUNK = 15
LINE_CLEAR = '\x1b[2K'

tenant = ''
username = ''
okapi_url = ''
access_token_cookie: Union[Cookie, None] = None
refresh_token_cookie: Union[Cookie, None] = None
headers = {}
client = httpx.AsyncClient()
dryrun = False
refresh_lock = asyncio.Lock()

# request timeout in seconds
ASYNC_CLIENT_TIMEOUT = 40

# limit the number of concurrent tasks.
# Try different values. Bigger values - for increasing performance, but could produce "Connection timeout exception"
MAX_CONCURRENT_TASKS = 7


# ---------------------------------------------------
# Utility functions

# Clears the current line (which could be the progress line) before printing
def clear_print(*args):
    print(LINE_CLEAR, *args)


def handle_login_response(resp):
    global access_token_cookie, refresh_token_cookie, headers

    if resp.status_code != 201:
        raise Exception(f'Status code: {resp.status_code}. Response: "{resp.text}"')
    refresh_token_cookie = None
    access_token_cookie = None
    for cookie in resp.cookies.jar:
        if cookie.name == 'folioRefreshToken':
            refresh_token_cookie = cookie
        elif cookie.name == 'folioAccessToken':
            access_token_cookie = cookie
    if refresh_token_cookie is None or access_token_cookie is None:
        print('\nError during login: missing cookie')
        raise SystemExit(1)
    headers = { 'x-okapi-tenant': tenant, 'x-okapi-token': access_token_cookie.value, 'Content-Type': 'application/json' }


async def refresh_login():
    async with refresh_lock:
        if not access_token_cookie.is_expired():
            # Token was refreshed by another concurrent task
            return
        # Refreshing access token...
        if refresh_token_cookie.is_expired():
            print('\nRefresh token has expired, refreshing the access token is unlikely to work. Trying user login.')
            await login()
            return
        login_headers = { 'x-okapi-tenant': tenant, 'Content-Type': 'application/json' }
        url = okapi_url + 'authn/refresh'
        try:
            jar = CookieJar()
            jar.set_cookie(refresh_token_cookie)
            resp = await client.post(url=url, headers=login_headers, cookies=jar, timeout=ASYNC_CLIENT_TIMEOUT)
            handle_login_response(resp)
        except Exception as err:
            print('\nError during login refresh:', err)
            raise SystemExit(1)


async def login():
    password = getpass.getpass('Password:')
    print()
    login_headers = { 'x-okapi-tenant': tenant, 'Content-Type': 'application/json' }
    data = { 'username': username, 'password': password }
    url = okapi_url + 'authn/login-with-expiry'
    try:
        resp = await client.post(url=url, headers=login_headers, json=data, timeout=ASYNC_CLIENT_TIMEOUT)
        handle_login_response(resp)
    except Exception as err:
        print('Error during login:', err)
        raise SystemExit(1)


async def async_request_without_retry(method, params):
    params['headers'] = headers
    params['timeout'] = ASYNC_CLIENT_TIMEOUT
    if method == 'get':
        resp = await client.get(**params)
    elif method == 'post':
        resp = await client.post(**params)
    elif method == 'put':
        resp = await client.put(**params)
    else:
        print('\nError with async request: ', method, params)
        raise SystemExit(1)
    return resp


async def async_request(method, params):
    previous_access_token_value = access_token_cookie.value
    try:
        resp = await async_request_without_retry(method, params)
    except httpx.ReadTimeout:
        print(f'\nTimeout error for {method}', params)
        print('Trying the same request again. If it fails try increasing ASYNC_CLIENT_TIMEOUT.')
        resp = await async_request_without_retry(method, params)
    if resp.status_code in [401, 403]:
        if access_token_cookie.is_expired():
            await refresh_login()
            # Trying the request again after the token was refreshed...
            resp = await async_request_without_retry(method, params)
        elif previous_access_token_value != access_token_cookie.value:
            # The access token was refreshed during the query, trying again...
            resp = await async_request_without_retry(method, params)
    return resp


async def get_request_without_query(url: str) -> dict:
    try:
        resp = await async_request('get', { 'url': url })
        if resp.status_code == HTTPStatus.OK:
            return resp.json()
        else:
            print(f'\nError getting record with url {url} ({resp.status_code}): \n{resp.text}')
            raise SystemExit(1)
    except Exception as err:
        print(f'\nError getting record with url {url} : {err=}')
        raise SystemExit(1)


async def get_request_with_params(url: str, params: dict, key: str) -> list:
    try:
        resp = await async_request('get', { 'url': url, 'params': params })
        if resp.status_code == HTTPStatus.OK:
            collection = resp.json()
            if key not in collection.keys():
                raise Exception(f'Could not find key in result of get request; url={url}, key={key}')
            return collection[key]
        else:
            print(f'\nError getting records with {url} and params {params} ({resp.status_code}): \n{resp.text}')
            raise SystemExit(1)
    except Exception as err:
        print(f'\nError getting records with {url} and params {params}: {err=}')
        raise SystemExit(1)


async def get_request(url: str, query: str, key: str) -> list:
    return await get_request_with_params(url, { 'query': query, 'offset': 0, 'limit': ITEM_MAX }, key)


async def post_request(url: str, data):
    if dryrun:
        return
    try:
        resp = await async_request('post', { 'url': url, 'data': json.dumps(data) })
        if resp.status_code == HTTPStatus.CREATED or resp.status_code == HTTPStatus.NO_CONTENT:
            return
        print(f'\nError in POST request {url} "{data}" ({resp.status_code}): {resp.text}')
        raise SystemExit(1)

    except Exception as err:
        print(f'\nError in POST request {url} "{data}": {err=}')
        raise SystemExit(1)


async def put_request(url: str, data):
    if dryrun:
        return
    try:
        resp = await async_request('put', { 'url': url, 'data': json.dumps(data) })
        if resp.status_code == HTTPStatus.NO_CONTENT:
            return
        print(f'\nError updating record {url} "{data}" ({resp.status_code}): {resp.text}')
        raise SystemExit(1)

    except Exception as err:
        print(f'\nError updating record {url} "{data}": {err=}')
        raise SystemExit(1)


async def get_fiscal_years_by_query(query) -> list:
    url = okapi_url + 'finance-storage/fiscal-years'
    return await get_request(url, query, 'fiscalYears')


async def get_chunk(url, query, key, last_id) -> list:
    if query is None:
        modified_query = ''
    else:
        modified_query = query + ' AND '
    if last_id is None:
        modified_query = modified_query + 'cql.allRecords=1 sortBy id'
    else:
        modified_query = modified_query + f'id > {last_id} sortBy id'
    params = { 'query': modified_query, 'offset': 0, 'limit': MAX_BY_CHUNK }
    return await get_request_with_params(url, params, key)


async def get_by_chunks(url, query, key) -> list:
    # See https://github.com/folio-org/raml-module-builder#implement-chunked-bulk-download
    records = []
    last_id = None
    while True:
        records_in_chunk = await get_chunk(url, query, key, last_id)
        if len(records_in_chunk) == 0:
            break
        records.extend(records_in_chunk)
        last_id = records_in_chunk[-1]['id']
    return records


async def get_ids_by_chunks(url, query, key) -> list:
    # Same as get_by_chunks but keeping only ids, to avoid loading a lot of records in memory
    ids = []
    last_id = None
    while True:
        records_in_chunk = await get_chunk(url, query, key, last_id)
        if len(records_in_chunk) == 0:
            break
        ids_chunk = list(map(lambda record: record['id'], records_in_chunk))
        ids.extend(ids_chunk)
        last_id = ids_chunk[-1]
    return ids


async def get_orders_by_query(query) -> list:
    try:
        orders = await get_by_chunks(okapi_url + 'orders-storage/purchase-orders', query, 'purchaseOrders')
    except Exception as err:
        print(f'\nError getting orders with query "{query}": {err}')
        raise SystemExit(1)
    return orders


async def get_order_by_id(order_id) -> dict:
    url = okapi_url + f"orders-storage/purchase-orders/{order_id}"
    return await get_request_without_query(url)


async def get_order_ids_by_query(query) -> list:
    try:
        ids = await get_ids_by_chunks(okapi_url + 'orders-storage/purchase-orders', query, 'purchaseOrders')
    except Exception as err:
        print(f'\nError getting order ids with query "{query}": {err}')
        raise SystemExit(1)
    return ids


async def get_transactions_by_query(query) -> list:
    url = okapi_url + 'finance-storage/transactions'
    transactions = await get_request(url, query, 'transactions')
    return transactions


async def get_polines_by_ids(poline_ids) -> list:
    polines = []
    for poline_ids_chunk in chunks(poline_ids, IDS_CHUNK):
        query = f"id==({' OR '.join(poline_ids_chunk)})"
        polines_chunk = await get_request(okapi_url + 'orders-storage/po-lines', query, 'poLines')
        polines.extend(polines_chunk)
    return polines


async def get_fiscal_year(fiscal_year_code) -> dict:
    query = f'code=="{fiscal_year_code}"'
    fiscal_years = await get_fiscal_years_by_query(query)
    if len(fiscal_years) == 0:
        print(f'\nCould not find fiscal year "{fiscal_year_code}".')
        raise SystemExit(1)
    return fiscal_years[0]


def test_fiscal_year_current(fiscal_year) -> bool:
    start = datetime.fromisoformat(fiscal_year['periodStart'])
    end = datetime.fromisoformat(fiscal_year['periodEnd'])
    now = datetime.now().astimezone()
    return start < now < end


async def get_ids_of_orders_with_status(status: str) -> list:
    print(f'Retrieving order ids for orders with status {status}...')
    query = f'workflowStatus=="{status}"'
    orders_ids = await get_order_ids_by_query(query)
    print(f'  {status} orders:', len(orders_ids))
    return orders_ids


async def get_ids_of_all_orders() -> list:
    print('Retrieving order ids for all orders...')
    orders_ids = await get_order_ids_by_query(None)
    print(f'  all orders:', len(orders_ids))
    return orders_ids


async def batch_update(transactions: list):
    batch = {'transactionsToUpdate': transactions}
    url = f'{okapi_url}finance-storage/transactions/batch-all-or-nothing'
    await post_request(url, batch)


async def batch_delete(transaction_ids: list):
    batch = {'idsOfTransactionsToDelete': transaction_ids}
    url = f'{okapi_url}finance-storage/transactions/batch-all-or-nothing'
    await post_request(url, batch)


async def get_budgets_by_query(query) -> list:
    budgets = await get_request(okapi_url + 'finance-storage/budgets', query, 'budgets')
    return budgets


async def get_budget_by_fund_id(fund_id, fiscal_year_id) -> dict:
    query = f'fundId=={fund_id} AND fiscalYearId=={fiscal_year_id}'
    budgets = await get_budgets_by_query(query)
    if len(budgets) == 0:
        print(f'\nCould not find budget for fund "{fund_id}" and fiscal year "{fiscal_year_id}".')
        raise SystemExit(1)
    return budgets[0]


async def get_budgets_by_fiscal_year(fiscal_year_id) -> list:
    query = f'fiscalYearId=={fiscal_year_id}'
    return await get_budgets_by_query(query)


async def get_order_encumbrances(order_id, fiscal_year_id, sem=None) -> list:
    url = okapi_url + 'finance-storage/transactions'
    query = f'encumbrance.sourcePurchaseOrderId=={order_id} AND fiscalYearId=={fiscal_year_id}'
    try:
        transactions = await get_request(url, query, 'transactions')
    finally:
        if sem is not None:
            sem.release()
    return transactions


def progress(index, total_elements, label=''):
    if total_elements == 0:
        return
    progress_length = 80
    current_progress_length = int(round(progress_length * index / float(total_elements)))

    percents_completed = round(100.0 * index / float(total_elements), 1)
    bar = '=' * current_progress_length + '-' * (progress_length - current_progress_length)

    sys.stdout.write('%s - [%s] %s%s \r' % (label, bar, percents_completed, '%'))
    sys.stdout.flush()

    if index == total_elements:
        print()


def end_progress():
    sys.stdout.write('                                                                                                        ')
    sys.stdout.write('\r')
    sys.stdout.flush()


async def update_poline(poline):
    url = f"{okapi_url}orders-storage/po-lines/{poline['id']}"
    await put_request(url, poline)


def chunks(iterable, n) -> list:
    # could be replaced by itertools.batched with python 3.12
    if n < 1:
        raise ValueError('n must be at least one')
    it = iter(iterable)
    while True:
        chunk_it = islice(it, n)
        try:
            first_el = next(chunk_it)
        except StopIteration:
            return
        yield list(chain((first_el,), chunk_it))


def flatten(l: list) -> list:
    return list(chain.from_iterable(l))


def print_summary(modifications: list):
    if len(modifications) == 0:
        print("\n  No modification was needed for this step")
        return
    print("\n  Summary of modifications:")
    modifications.sort(key = lambda m: (m['po_id'], m['poline_id']))
    current_po = None
    current_poline = None
    order_ids = set()
    poline_ids = set()
    encumbrance_ids = set()
    for modification in modifications:
        order_ids.add(modification['po_id'])
        poline_ids.add(modification['poline_id'])
        encumbrance_ids.add(modification['encumbrance_id'])
        if modification['po_id'] != current_po:
            current_po = modification['po_id']
            print(f"    Order: {current_po}")
        if modification['poline_id'] != current_poline:
            current_poline = modification['poline_id']
            print(f"      Line: {current_poline}")
        print(f"        Encumbrance: {modification['encumbrance_id']}")
    print(f"  Totals: orders: {len(order_ids)}  lines: {len(poline_ids)}  encumbrances: {len(encumbrance_ids)}")


# ---------------------------------------------------
# Remove duplicate encumbrances

def find_duplicates(encumbrances) -> list:
    duplicates = []

    for idx, enc1 in enumerate(encumbrances):
        from_fund_id_1 = enc1['fromFundId']
        source_po_line_id_1 = enc1['encumbrance']['sourcePoLineId']
        if 'expenseClassId' in enc1:
            expense_class_id_1 = enc1['expenseClassId']
        else:
            expense_class_id_1 = None
        fiscal_year_id_1 = enc1['fiscalYearId']
        for enc2 in encumbrances[idx + 1:]:
            from_fund_id_2 = enc2['fromFundId']
            source_po_line_id_2 = enc2['encumbrance']['sourcePoLineId']
            if 'expenseClassId' in enc2:
                expense_class_id_2 = enc2['expenseClassId']
            else:
                expense_class_id_2 = None
            fiscal_year_id_2 = enc2['fiscalYearId']
            if from_fund_id_2 == from_fund_id_1 and source_po_line_id_2 == source_po_line_id_1 and \
                    expense_class_id_2 == expense_class_id_1 and fiscal_year_id_2 == fiscal_year_id_1:
                duplicates.append((enc1, enc2))
                break
    return duplicates


def prepare_encumbrance_changes(duplicates, order, polines, fy_is_current) -> list:
    ids_of_encumbrances_in_fund_distributions = []
    for poline in polines:
        if 'fundDistribution' in poline:
            for fd in poline['fundDistribution']:
                if 'encumbrance' in fd:
                    ids_of_encumbrances_in_fund_distributions.append(fd['encumbrance'])

    order_status = order['workflowStatus']
    order_type = order['orderType']
    reencumber = order['reEncumber']

    encumbrance_changes = []
    for (enc1, enc2) in duplicates:
        remove = None
        if fy_is_current:
            for enc in (enc1, enc2):
                if enc['id'] not in ids_of_encumbrances_in_fund_distributions:
                    remove = enc
                    break
        if remove is None and order_status == 'Closed':
            for enc in (enc1, enc2):
                if enc['encumbrance']['status'] != 'Released':
                    remove = enc
                    break
        if remove is None:
            for enc in (enc1, enc2):
                if enc['encumbrance']['orderStatus'] != order_status or \
                        enc['encumbrance']['orderType'] != order_type or enc['encumbrance']['reEncumber'] != reencumber:
                    remove = enc
                    break
        if remove is None:
            remove = enc1
        replace_by = enc2 if remove is enc1 else enc1
        encumbrance_changes.append({'remove': remove, 'replace_by': replace_by})

    return encumbrance_changes


async def update_poline_encumbrance(encumbrance_to_remove, replace_by, poline=None):
    url = okapi_url + f"orders-storage/po-lines/{encumbrance_to_remove['encumbrance']['sourcePoLineId']}"
    if poline is None:
        poline = await get_request_without_query(url)
    for fd in poline['fundDistribution']:
        if 'encumbrance' in fd and fd['encumbrance'] == encumbrance_to_remove['id']:
            fd['encumbrance'] = replace_by['id']
            await put_request(url, poline)
            break


async def remove_encumbrances_and_update_polines(encumbrance_changes):
    futures = []
    ids_to_delete = []
    for change in encumbrance_changes:
        encumbrance_to_remove = change['remove']
        replace_by = change['replace_by']
        futures.append(asyncio.ensure_future(update_poline_encumbrance(encumbrance_to_remove, replace_by)))
        ids_to_delete.append(encumbrance_to_remove['id'])
    await asyncio.gather(*futures)
    await batch_delete(ids_to_delete)


async def remove_duplicate_encumbrances_in_order(order_id, fiscal_year_id, fy_is_current, sem) -> list:
    modifications = []
    try:
        order_encumbrances = await get_order_encumbrances(order_id, fiscal_year_id)
        if len(order_encumbrances) == 0:
            return modifications
        duplicates = find_duplicates(order_encumbrances)
        if len(duplicates) == 0:
            return modifications
        # there are duplicates, let's get the order and fund distributions to better choose which encumbrances to remove
        order = await get_order_by_id(order_id)
        polines = await get_polines_by_order_id(order_id)
        encumbrance_changes = prepare_encumbrance_changes(duplicates, order, polines, fy_is_current)
        if len(encumbrance_changes) == 0:
            return modifications
        for change in encumbrance_changes:
            enc = change['remove']
            modifications.append({ 'po_id': enc['encumbrance']['sourcePurchaseOrderId'], 'poline_id': enc['encumbrance']['sourcePoLineId'], 'encumbrance_id': enc['id'] })
        await remove_encumbrances_and_update_polines(encumbrance_changes)
        return modifications
    finally:
        sem.release()


async def remove_duplicate_encumbrances(open_and_closed_orders_ids, fiscal_year_id, fy_is_current):
    print('\nRemoving duplicate encumbrances for open and closed orders...')
    futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_id in enumerate(open_and_closed_orders_ids):
        await sem.acquire()
        progress(idx, len(open_and_closed_orders_ids))
        futures.append(asyncio.ensure_future(remove_duplicate_encumbrances_in_order(order_id, fiscal_year_id,
            fy_is_current, sem)))

    modifications = flatten(await asyncio.gather(*futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Fix poline-encumbrance relations

async def get_polines_by_order_id(order_id) -> list:
    query = f'purchaseOrderId=={order_id}'
    po_lines = await get_request(okapi_url + 'orders-storage/po-lines', query, 'poLines')
    return po_lines


async def update_encumbrance_fund_id(encumbrance, new_fund_id, poline):
    encumbrance['fromFundId'] = new_fund_id
    encumbrance_id = encumbrance['id']
    clear_print(f"  Fixing fromFundId for po line {poline['id']} ({poline['poLineNumber']}) encumbrance {encumbrance_id}")
    await batch_update([encumbrance])


# Remove a duplicate encumbrance if it has a wrong fromFundId, and update the poline fd if needed
async def fix_fund_id_with_duplicate_encumbrances(encumbrances, fd_fund_id, poline, order_encumbrances) -> list:
    modifications = []
    encumbrances_with_right_fund = []
    encumbrances_with_bad_fund = []
    for encumbrance in encumbrances:
        if encumbrance['fromFundId'] == fd_fund_id:
            encumbrances_with_right_fund.append(encumbrance)
        else:
            encumbrances_with_bad_fund.append(encumbrance)
    if len(encumbrances_with_bad_fund) == 0:
        clear_print(f"  Warning: there is a remaining duplicate encumbrance for poline {poline['id']} "
              f"({poline['poLineNumber']}).")
        return modifications
    if len(encumbrances_with_right_fund) != 1:
        clear_print(f"  Problem fixing encumbrances for poline {poline['id']} ({poline['poLineNumber']}), "
              "please fix by hand.")
        return modifications
    replace_by = encumbrances_with_right_fund[0]
    ids_to_delete = []
    for encumbrance_to_remove in encumbrances_with_bad_fund:
        clear_print(f"  Removing encumbrance {encumbrance_to_remove['id']} for po line {poline['id']} "
              f"({poline['poLineNumber']})")
        modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': encumbrance_to_remove['id'] })
        await update_poline_encumbrance(encumbrance_to_remove, replace_by, poline)
        ids_to_delete.append(encumbrance_to_remove['id'])
        order_encumbrances.remove(encumbrance_to_remove)
    await batch_delete(ids_to_delete)
    return modifications


# Fix encumbrance fromFundId if it doesn't match the po line fund distribution (see MODFISTO-384, MODFISTO-385)
async def fix_poline_encumbrance_fund_id(poline, order_encumbrances) -> list:
    modifications = []
    fds = poline['fundDistribution']
    # we can't fix the fundId if there is more than 1 fund distribution in the po line
    if len(fds) != 1:
        return modifications

    fd_fund_id = fds[0]['fundId']
    encumbrances = []
    for enc in order_encumbrances:
        if enc['encumbrance']['sourcePoLineId'] == poline['id']:
            encumbrances.append(enc)
    if len(encumbrances) == 0:
        return modifications
    if len(encumbrances) == 1:
        enc = encumbrances[0]
        if enc['fromFundId'] == fd_fund_id:
            return modifications
        await update_encumbrance_fund_id(enc, fd_fund_id, poline)
        modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': enc['id'] })
        return modifications
    modifications = await fix_fund_id_with_duplicate_encumbrances(encumbrances, fd_fund_id, poline, order_encumbrances)
    return modifications


def look_for_matching_encumbrance(order_encumbrances, poline_id, fund_id, expense_class_id):
    for enc in order_encumbrances:
        if enc['encumbrance']['sourcePoLineId'] == poline_id and enc['fromFundId'] == fund_id and enc.get('expenseClassId') == expense_class_id:
            return enc
    return None


def calculate_total_cost(cost):
    total = 0
    if 'listUnitPrice' in cost and 'quantityPhysical' in cost:
        total = total + cost['listUnitPrice'] * cost['quantityPhysical']
    if 'listUnitPriceElectronic' in cost and 'quantityElectronic' in cost:
        total = total + cost['listUnitPriceElectronic'] * cost['quantityElectronic']
    if 'discount' in cost:
        if cost['discountType'] == 'amount':
            discount = cost['discount']
        else:
            discount = round(total * cost['discount'] / 100, 2)
        total = total - discount
    if 'additionalCost' in cost:
        total = total + cost['additionalCost']
    return total


def update_poline_like_rollover(poline, initial_encumbrances):
    poline_number = poline['poLineNumber']
    poline_cost = poline['cost']
    poline_currency = poline_cost['currency']
    if any(encumbrance['currency'] != poline_currency for encumbrance in initial_encumbrances):
        clear_print(f"  Warning: can't rollover po line {poline_number} because the po line is not using the system currency")
        return
    previous_poline_estimated_price = float(poline_cost['poLineEstimatedPrice'])
    total_cost = calculate_total_cost(poline_cost)
    total_initial_encumbrances = sum([enc['encumbrance']['initialAmountEncumbered'] for enc in initial_encumbrances])
    fyro_adjustment = total_initial_encumbrances - total_cost
    if 'fyroAdjustmentAmount' not in poline_cost or poline_cost['fyroAdjustmentAmount'] != fyro_adjustment:
        clear_print(f"  Updating po line {poline_number} fyroAdjustmentAmount to {fyro_adjustment}")
        poline_cost['fyroAdjustmentAmount'] = fyro_adjustment
    if 'poLineEstimatedPrice' not in poline_cost or poline_cost['poLineEstimatedPrice'] != total_initial_encumbrances:
        clear_print(f"  Updating po line {poline_number} poLineEstimatedPrice to {total_initial_encumbrances}")
        poline_cost['poLineEstimatedPrice'] = total_initial_encumbrances
    for fd in poline['fundDistribution']:
        if fd['distributionType'] == 'amount':
            if previous_poline_estimated_price == 0:
                new_fd_value = 0.0
            else:
                new_fd_value = round((float(fd['value']) / previous_poline_estimated_price) * total_initial_encumbrances, 2)
            if new_fd_value != float(fd['value']):
                clear_print(f"  Updating po line {poline_number} fund distribution value for {fd['code']} from {fd['value']} to {new_fd_value}")
                fd['value'] = new_fd_value


# Update or remove the po line encumbrance links, and do a rollover if needed.
# It is assumed that, if an encumbrance link is not up to date, the FYRO failed to process the po line.
# In this case, the po line needs to be updated as in the order rollover.
# See MODFIN-452 and mod-orders OrderRolloverService.java.
async def fix_poline_encumbrance_links(poline, order_encumbrances) -> list:
    modifications = []
    poline_needs_update = False
    encumbrance_link_was_updated = False
    initial_encumbrances = []
    poline_number = poline['poLineNumber']

    for fd in poline['fundDistribution']:
        matching_encumbrance = look_for_matching_encumbrance(order_encumbrances, poline['id'], fd['fundId'], fd.get('expenseClassId'))
        if matching_encumbrance is None:
            if 'encumbrance' in fd:
                clear_print(f"  Removing link from po line {poline_number} to encumbrance {fd['encumbrance']}")
                modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': fd['encumbrance'] })
                del fd['encumbrance']
                poline_needs_update = True
        else:
            initial_encumbrances.append(matching_encumbrance)
            if 'encumbrance' not in fd:
                clear_print(f"  Adding link for po line {poline_number} to encumbrance {matching_encumbrance['id']}")
                if 'encumbrance' in fd:
                    modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': fd['encumbrance'] })
                modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': matching_encumbrance['id'] })
                fd['encumbrance'] = matching_encumbrance['id']
                poline_needs_update = True
            elif matching_encumbrance['id'] != fd['encumbrance']:
                clear_print(f"  Updating link for po line {poline_number} from encumbrance {fd['encumbrance']} to "
                      f"encumbrance {matching_encumbrance['id']}")
                modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': fd['encumbrance'] })
                modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': matching_encumbrance['id'] })
                fd['encumbrance'] = matching_encumbrance['id']
                encumbrance_link_was_updated = True
                poline_needs_update = True

    if encumbrance_link_was_updated:
        update_poline_like_rollover(poline, initial_encumbrances)

    if poline_needs_update:
        await update_poline(poline)

    return modifications


async def process_poline_encumbrances_relations(poline, order_encumbrances) -> list:
    poline_modifications = []
    poline_modifications.extend(await fix_poline_encumbrance_fund_id(poline, order_encumbrances))
    poline_modifications.extend(await fix_poline_encumbrance_links(poline, order_encumbrances))
    return poline_modifications


# Get encumbrances for the fiscal year and call process_po_line_encumbrances_relations() for each po line
async def process_order_encumbrances_relations(order_id, fiscal_year_id, order_sem) -> list:
    po_modifications = []
    po_lines = await get_polines_by_order_id(order_id)
    if len(po_lines) == 0:
        order_sem.release()
        return po_modifications
    order_encumbrances = await get_order_encumbrances(order_id, fiscal_year_id)
    if len(order_encumbrances) == 0:
        order_sem.release()
        return po_modifications

    for po_line in po_lines:
        po_line_modifications = await process_poline_encumbrances_relations(po_line, order_encumbrances)
        po_modifications.extend(po_line_modifications)

    order_sem.release()
    return po_modifications


# Call process_order_encumbrances_relations() for each order
async def fix_poline_encumbrances_relations(all_orders_ids, fiscal_year_id):
    print('\nFixing poline-encumbrance links for all orders in the current FY...')
    if len(all_orders_ids) == 0:
        print('  Found no order.')
        return
    orders_futures = []
    order_sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_id in enumerate(all_orders_ids):
        await order_sem.acquire()
        progress(idx, len(all_orders_ids))
        orders_futures.append(asyncio.ensure_future(process_order_encumbrances_relations(
            order_id, fiscal_year_id, order_sem)))
    modifications = flatten(await asyncio.gather(*orders_futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Fix encumbrance orderStatus for closed orders

async def get_encumbrances_to_fix_order_status(order_id, fiscal_year_id) -> list:
    query = f'encumbrance.orderStatus<>"Closed" AND encumbrance.sourcePurchaseOrderId=={order_id} AND ' \
            f'fiscalYearId=={fiscal_year_id}'
    url = okapi_url + 'finance-storage/transactions'
    return await get_request(url, query, 'transactions')


async def fix_encumbrances_order_status(order_id, encumbrances) -> list:
    modifications = []
    try:
        for enc in encumbrances:
            modifications.append({ 'po_id': enc['encumbrance']['sourcePurchaseOrderId'], 'poline_id': enc['encumbrance']['sourcePoLineId'], 'encumbrance_id': enc['id'] })
            enc['encumbrance']['orderStatus'] = 'Closed'
        await batch_update(encumbrances)
    except Exception as err:
        print(f'Error when fixing order status in encumbrances for order {order_id}:', err)
        raise SystemExit(1)
    return modifications


async def fix_encumbrance_order_status_for_closed_order(order_id, fiscal_year_id, sem) -> list:
    modifications = []
    try:
        encumbrances = await get_encumbrances_to_fix_order_status(order_id, fiscal_year_id)
        if len(encumbrances) != 0:
            modifications = await fix_encumbrances_order_status(order_id, encumbrances)
    finally:
        sem.release()
    return modifications


async def fix_encumbrance_order_status_for_closed_orders(closed_orders_ids, fiscal_year_id):
    print('\nFixing encumbrance order status for closed orders...')
    if len(closed_orders_ids) == 0:
        print('  Found no closed orders.')
        return
    fix_encumbrance_futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_id in enumerate(closed_orders_ids):
        await sem.acquire()
        progress(idx, len(closed_orders_ids))
        fixed_encumbrance_future = asyncio.ensure_future(fix_encumbrance_order_status_for_closed_order(
            order_id, fiscal_year_id, sem))
        fix_encumbrance_futures.append(fixed_encumbrance_future)
    modifications = flatten(await asyncio.gather(*fix_encumbrance_futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Fix encumbrance properties (orderStatus, orderType, reEncumber) for open and pending orders

async def get_encumbrances_to_fix_properties(order, fiscal_year_id) -> list:
    workflow_status = order['workflowStatus']
    order_type = order['orderType']
    reencumber = str(order['reEncumber']).lower()
    query = f'encumbrance.sourcePurchaseOrderId=={order["id"]} AND fiscalYearId=={fiscal_year_id} AND ' \
            f'(encumbrance.orderStatus<>"{workflow_status}" OR ' \
            f'encumbrance.orderType<>"{order_type}" OR encumbrance.reEncumber<>{reencumber})'
    url = okapi_url + 'finance-storage/transactions'
    return await get_request(url, query, 'transactions')


async def fix_encumbrances_properties(order, encumbrances) -> list:
    modifications = []
    try:
        for enc in encumbrances:
            modifications.append({ 'po_id': order['id'], 'poline_id': enc['encumbrance']['sourcePoLineId'], 'encumbrance_id': enc['id'] })
            enc['encumbrance']['orderStatus'] = order['workflowStatus']
            enc['encumbrance']['orderType'] = order['orderType']
            enc['encumbrance']['reEncumber'] = order['reEncumber']
        await batch_update(encumbrances)
    except Exception as err:
        print(f"Error when fixing encumbrance properties for order {order['id']}:", err)
        raise SystemExit(1)
    return modifications


async def fix_encumbrance_properties_for_open_or_pending_order(order, fiscal_year_id, sem) -> list:
    modifications = []
    try:
        encumbrances = await get_encumbrances_to_fix_properties(order, fiscal_year_id)
        if len(encumbrances) != 0:
            modifications = await fix_encumbrances_properties(order, encumbrances)
    finally:
        sem.release()
    return modifications


async def fix_encumbrance_properties_for_open_and_pending_orders(fiscal_year_id):
    print('\nFixing encumbrance properties (orderStatus, orderType, reEncumber) for open and pending orders...')
    query = 'workflowStatus==("Open" OR "Pending")'
    open_and_pending_orders = await get_orders_by_query(query)
    if len(open_and_pending_orders) == 0:
        print('  Found no open or pending order.')
        return
    fix_encumbrance_futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order in enumerate(open_and_pending_orders):
        await sem.acquire()
        progress(idx, len(open_and_pending_orders))
        fixed_encumbrance_future = asyncio.ensure_future(fix_encumbrance_properties_for_open_or_pending_order(
            order, fiscal_year_id, sem))
        fix_encumbrance_futures.append(fixed_encumbrance_future)
    modifications = flatten(await asyncio.gather(*fix_encumbrance_futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Remove pending order links to encumbrances in previous fiscal years

async def get_orders_encumbrances_with_different_fy(order_ids, fiscal_year_id) -> list:
    url = okapi_url + 'finance-storage/transactions'
    ids = f"({' OR '.join(order_ids)})"
    query = f'encumbrance.sourcePurchaseOrderId=={ids} AND fiscalYearId<>{fiscal_year_id}'
    transactions = await get_by_chunks(url, query, 'transactions')
    return transactions


async def get_active_budgets_by_fund_ids(fund_ids) -> list:
    budgets = []
    for fund_ids_chunk in chunks(fund_ids, IDS_CHUNK):
        query = f"budgetStatus==Active AND fundId==({' OR '.join(fund_ids_chunk)})"
        budgets_chunk = await get_budgets_by_query(query)
        budgets.extend(budgets_chunk)
    return budgets


async def select_encumbrances_without_active_budgets(encumbrances) -> list:
    if len(encumbrances) == 0:
        return []
    fund_ids = set(map(lambda enc: enc['fromFundId'], encumbrances))
    budgets = await get_active_budgets_by_fund_ids(fund_ids)
    return [enc for enc in encumbrances
        if not any(
            budget['fundId'] == enc['fromFundId'] and budget['fiscalYearId'] == enc['fiscalYearId']
            for budget in budgets
        )]


async def remove_links_to_encumbrances(encumbrances) -> list:
    modifications = []
    poline_ids = list(set(map(lambda enc: enc['encumbrance']['sourcePoLineId'], encumbrances)))
    encumbrance_ids = list(map(lambda enc: enc['id'], encumbrances))
    polines = await get_polines_by_ids(poline_ids)
    for poline in polines:
        for fd in poline['fundDistribution']:
            if 'encumbrance' in fd and fd['encumbrance'] in encumbrance_ids:
                modifications.append({ 'po_id': poline['purchaseOrderId'], 'poline_id': poline['id'], 'encumbrance_id': fd['encumbrance'] })
                del fd['encumbrance']
        await update_poline(poline)
    return modifications


async def remove_pending_order_links(order_ids, fiscal_year_id, sem) -> list:
    modifications = []
    encumbrances = await get_orders_encumbrances_with_different_fy(order_ids, fiscal_year_id)
    filtered_encumbrances = await select_encumbrances_without_active_budgets(encumbrances)
    if len(filtered_encumbrances) != 0:
        modifications = await remove_links_to_encumbrances(filtered_encumbrances)
    sem.release()
    return modifications


async def remove_pending_order_links_to_encumbrances_in_other_fy(pending_orders_ids, fiscal_year_id):
    print('\nRemove pending order links to encumbrances in previous fiscal years...')
    if len(pending_orders_ids) == 0:
        print('  Found no pending orders.')
        return
    fix_encumbrance_futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_ids in enumerate(chunks(pending_orders_ids, IDS_CHUNK)):
        await sem.acquire()
        progress(idx, len(pending_orders_ids))
        fixed_encumbrance_future = asyncio.ensure_future(remove_pending_order_links(order_ids, fiscal_year_id, sem))
        fix_encumbrance_futures.append(fixed_encumbrance_future)
    modifications = flatten(await asyncio.gather(*fix_encumbrance_futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Unrelease open orders encumbrances with non-zero amounts

async def unrelease_encumbrances(order_id, encumbrances) -> list:
    modifications = []
    for encumbrance in encumbrances:
        modifications.append({ 'po_id': order_id, 'poline_id': encumbrance['encumbrance']['sourcePoLineId'], 'encumbrance_id': encumbrance['id'] })
        encumbrance['encumbrance']['status'] = 'Unreleased'
    await batch_update(encumbrances)
    return modifications


async def unrelease_encumbrances_with_non_zero_amounts(order_id, fiscal_year_id, sem) -> list:
    modifications = []
    query = f'amount<>0.0 AND encumbrance.status=="Released" AND encumbrance.sourcePurchaseOrderId=={order_id} AND ' \
            f'fiscalYearId=={fiscal_year_id}'
    order_encumbrances = await get_request(okapi_url + 'finance-storage/transactions', query, 'transactions')

    # unrelease encumbrances by order id
    if len(order_encumbrances) != 0:
        modifications = await unrelease_encumbrances(order_id, order_encumbrances)

    sem.release()
    return modifications


async def unrelease_open_orders_encumbrances_with_nonzero_amounts(fiscal_year_id, open_orders_ids):
    print('\nUnreleasing open orders encumbrances with non-zero amounts...')
    if len(open_orders_ids) == 0:
        print('  Found no open orders.')
        return
    enc_futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_id in enumerate(open_orders_ids):
        await sem.acquire()
        progress(idx, len(open_orders_ids))
        enc_futures.append(asyncio.ensure_future(unrelease_encumbrances_with_non_zero_amounts(
            order_id, fiscal_year_id, sem)))
    modifications = flatten(await asyncio.gather(*enc_futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Release open orders encumbrances with negative amounts (see MODFISTO-368)

async def release_encumbrances(order_id, encumbrances) -> list:
    modifications = []
    for encumbrance in encumbrances:
        modifications.append({ 'po_id': order_id, 'poline_id': encumbrance['encumbrance']['sourcePoLineId'], 'encumbrance_id': encumbrance['id'] })
        encumbrance['encumbrance']['status'] = 'Released'
    await batch_update(encumbrances)
    return modifications


async def release_encumbrances_with_negative_amounts(order_id, fiscal_year_id, sem) -> list:
    modifications = []
    query = 'amount </number 0 AND encumbrance.status=="Unreleased" AND ' \
            f'(encumbrance.amountAwaitingPayment >/number 0 OR encumbrance.amountExpended >/number 0) AND ' \
            f'encumbrance.sourcePurchaseOrderId=={order_id} AND fiscalYearId=={fiscal_year_id}'
    order_encumbrances = await get_request(okapi_url + 'finance-storage/transactions', query, 'transactions')

    # release encumbrances by order id
    if len(order_encumbrances) != 0:
        modifications = await release_encumbrances(order_id, order_encumbrances)

    sem.release()
    return modifications


async def release_open_orders_encumbrances_with_negative_amounts(fiscal_year_id, open_orders_ids):
    print('\nReleasing open orders encumbrances with negative amounts...')
    if len(open_orders_ids) == 0:
        print('  Found no open orders.')
        return
    enc_futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_id in enumerate(open_orders_ids):
        await sem.acquire()
        progress(idx, len(open_orders_ids))
        enc_futures.append(asyncio.ensure_future(release_encumbrances_with_negative_amounts(
            order_id, fiscal_year_id, sem)))
    modifications = flatten(await asyncio.gather(*enc_futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Release cancelled order line encumbrances (see MODFISTO-383)

def find_encumbrances_to_release(po_lines, order_encumbrances) -> list:
    encumbrances_to_release = []
    for pol in po_lines:
        if pol['paymentStatus'] == 'Cancelled':
            for enc in order_encumbrances:
                if enc['encumbrance']['sourcePoLineId'] == pol['id'] and enc['encumbrance']['status'] == 'Unreleased':
                    encumbrances_to_release.append(enc)
    return encumbrances_to_release


async def release_cancelled_pol_encumbrances(order_id, fiscal_year_id, sem) -> list:
    modifications = []
    po_lines = await get_polines_by_order_id(order_id)
    if len(po_lines) == 0:
        sem.release()
        return modifications
    order_encumbrances = await get_order_encumbrances(order_id, fiscal_year_id)
    if len(order_encumbrances) == 0:
        sem.release()
        return modifications
    encumbrances_to_release = find_encumbrances_to_release(po_lines, order_encumbrances)
    if len(encumbrances_to_release) == 0:
        sem.release()
        return modifications
    for enc in encumbrances_to_release:
        modifications.append({ 'po_id': enc['encumbrance']['sourcePurchaseOrderId'], 'poline_id': enc['encumbrance']['sourcePoLineId'], 'encumbrance_id': enc['id'] })
    await release_encumbrances(order_id, encumbrances_to_release)
    sem.release()
    return modifications


async def release_cancelled_order_line_encumbrances(fiscal_year_id, open_orders_ids):
    print('\nReleasing cancelled order line encumbrances...')
    if len(open_orders_ids) == 0:
        print('  Found no open orders.')
        return
    enc_futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_id in enumerate(open_orders_ids):
        await sem.acquire()
        progress(idx, len(open_orders_ids))
        enc_futures.append(asyncio.ensure_future(release_cancelled_pol_encumbrances(order_id, fiscal_year_id, sem)))
    modifications = flatten(await asyncio.gather(*enc_futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# Recalculate budget encumbered

async def update_budgets(encumbered, fund_id, fiscal_year_id, sem) -> int:
    nb_modified = 0
    budget = await get_budget_by_fund_id(fund_id, fiscal_year_id)

    # Cast into decimal values, so 0 == 0.0 == 0.00 will return true
    if Decimal(str(budget['encumbered'])) != Decimal(encumbered):
        clear_print(f"    Budget \"{budget['name']}\": changing encumbered from {budget['encumbered']} to {encumbered}")
        budget['encumbered'] = encumbered

        url = f"{okapi_url}finance-storage/budgets/{budget['id']}"
        await put_request(url, budget)
        nb_modified = 1
    sem.release()
    return nb_modified


async def recalculate_budget_encumbered(open_and_closed_orders_ids, fiscal_year_id):
    # Recalculate the encumbered property for all the budgets related to these encumbrances
    # Take closed orders into account because we might have to set a budget encumbered to 0
    print(f'\nRecalculating budget encumbered for {len(open_and_closed_orders_ids)} orders ...')
    enc_future = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)
    for idx, order_id in enumerate(open_and_closed_orders_ids):
        await sem.acquire()
        progress(idx, len(open_and_closed_orders_ids))
        enc_future.append(asyncio.ensure_future(get_order_encumbrances(order_id, fiscal_year_id, sem)))

    encumbrances = flatten(await asyncio.gather(*enc_future))
    end_progress()

    encumbered_for_fund = {}
    budgets = await get_budgets_by_fiscal_year(fiscal_year_id)
    for budget in budgets:
        fund_id = budget['fundId']
        if fund_id not in encumbered_for_fund:
            encumbered_for_fund[fund_id] = 0

    for encumbrance in encumbrances:
        fund_id = encumbrance['fromFundId']
        if fund_id in encumbered_for_fund:
            encumbered_for_fund[fund_id] += Decimal(str(encumbrance['amount']))

    print('  Updating budgets...')

    update_budget_futures = []
    for fund_id, encumbered in encumbered_for_fund.items():
        await sem.acquire()
        update_budget_futures.append(asyncio.ensure_future(update_budgets(
            str(encumbered), fund_id, fiscal_year_id, sem)))
    nb_modified = sum(await asyncio.gather(*update_budget_futures))

    print(f'  Edited {nb_modified} budget(s).')


# ---------------------------------------------------
# Release unreleased encumbrances for closed orders

async def get_order_encumbrances_to_release(order_id, fiscal_year_id) -> list:
    query = f'encumbrance.status=="Unreleased" AND encumbrance.sourcePurchaseOrderId=={order_id} AND ' \
            f'fiscalYearId=={fiscal_year_id}'
    return await get_transactions_by_query(query)


async def release_order_encumbrances(order_id, fiscal_year_id, sem) -> list:
    modifications = []
    encumbrances = await get_order_encumbrances_to_release(order_id, fiscal_year_id)
    if len(encumbrances) != 0:
        for enc in encumbrances:
            modifications.append({ 'po_id': enc['encumbrance']['sourcePurchaseOrderId'], 'poline_id': enc['encumbrance']['sourcePoLineId'], 'encumbrance_id': enc['id'] })
        await release_encumbrances(order_id, encumbrances)
    sem.release()
    return modifications


async def release_unreleased_encumbrances_for_closed_orders(closed_orders_ids, fiscal_year_id):
    print('\nReleasing unreleased encumbrances for closed orders...')
    if len(closed_orders_ids) == 0:
        print('  Found no closed orders.')
        return
    futures = []
    sem = asyncio.Semaphore(MAX_CONCURRENT_TASKS)

    for idx, order_id in enumerate(closed_orders_ids):
        await sem.acquire()
        progress(idx, len(closed_orders_ids))
        futures.append(asyncio.ensure_future(release_order_encumbrances(order_id, fiscal_year_id, sem)))
    modifications = flatten(await asyncio.gather(*futures))
    end_progress()
    print_summary(modifications)


# ---------------------------------------------------
# All operations

def print_hr():
    print('\n---------------------------------------------------')


async def all_operations(closed_orders_ids, open_orders_ids, pending_orders_ids, fiscal_year_id, fy_is_current):
    open_and_closed_orders_ids = closed_orders_ids + open_orders_ids
    print_hr()
    await remove_duplicate_encumbrances(open_and_closed_orders_ids, fiscal_year_id, fy_is_current)
    if fy_is_current:
        print_hr()
        await fix_poline_encumbrances_relations(open_orders_ids + closed_orders_ids + pending_orders_ids, fiscal_year_id)
        print_hr()
        await fix_encumbrance_order_status_for_closed_orders(closed_orders_ids, fiscal_year_id)
        print_hr()
        await fix_encumbrance_properties_for_open_and_pending_orders(fiscal_year_id)
        print_hr()
        await remove_pending_order_links_to_encumbrances_in_other_fy(pending_orders_ids, fiscal_year_id)
    print_hr()
    await unrelease_open_orders_encumbrances_with_nonzero_amounts(fiscal_year_id, open_orders_ids)
    print_hr()
    await release_open_orders_encumbrances_with_negative_amounts(fiscal_year_id, open_orders_ids)
    print_hr()
    await release_cancelled_order_line_encumbrances(fiscal_year_id, open_orders_ids)
    print_hr()
    await recalculate_budget_encumbered(open_and_closed_orders_ids, fiscal_year_id)
    print_hr()
    await release_unreleased_encumbrances_for_closed_orders(closed_orders_ids, fiscal_year_id)


# ---------------------------------------------------
# Dry-run mode selection

def dryrun_mode_selection():
    global dryrun

    choice_i = 0
    while choice_i < 1 or choice_i > 2:
        print('1) Dry-run mode (read-only, will not apply fixes)')
        print('2) Normal mode (will apply fixes)')
        choice_s = input('Choose an option: ')
        try:
            choice_i = int(choice_s)
            if choice_i < 1 or choice_i > 2:
                print('Invalid option.')
        except ValueError:
            print('Invalid option.')
    if choice_i == 1:
        dryrun = True
        print("Dry-run mode enabled. Fixes *will not* actually be applied.")
    else:
        print("Normal mode. All fixes *will* be applied.")
    print()


# ---------------------------------------------------
# Menu and running operations

async def run_operation(choice, fiscal_year_code):
    initial_time = time.time()
    await login()
    fiscal_year = await get_fiscal_year(fiscal_year_code)
    fy_is_current = test_fiscal_year_current(fiscal_year)
    fiscal_year_id = fiscal_year['id']

    if choice == 1:
        closed_orders_ids = await get_ids_of_orders_with_status('Closed')
        open_orders_ids = await get_ids_of_orders_with_status('Open')
        pending_orders_ids = await get_ids_of_orders_with_status('Pending')
        await all_operations(closed_orders_ids, open_orders_ids, pending_orders_ids, fiscal_year_id, fy_is_current)
    elif choice == 2:
        closed_orders_ids = await get_ids_of_orders_with_status('Closed')
        open_orders_ids = await get_ids_of_orders_with_status('Open')
        open_and_closed_orders_ids = closed_orders_ids + open_orders_ids
        await remove_duplicate_encumbrances(open_and_closed_orders_ids, fiscal_year_id, fy_is_current)
    elif choice == 3:
        if not fy_is_current:
            print('Fiscal year is not current - fixing po line encumbrance relations is not needed.')
        else:
            all_orders_ids = await get_ids_of_all_orders()
            await fix_poline_encumbrances_relations(all_orders_ids, fiscal_year_id)
    elif choice == 4:
        if not fy_is_current:
            print('Fiscal year is not current - fixing encumbrance order status is not needed.')
        else:
            closed_orders_ids = await get_ids_of_orders_with_status('Closed')
            await fix_encumbrance_order_status_for_closed_orders(closed_orders_ids, fiscal_year_id)
    elif choice == 5:
        if not fy_is_current:
            print('Fiscal year is not current - fixing encumbrance properties is not needed.')
        else:
            await fix_encumbrance_properties_for_open_and_pending_orders(fiscal_year_id)
    elif choice == 6:
        if not fy_is_current:
            print('Fiscal year is not current - removing pending orders encumbrance links is not needed.')
        else:
            pending_orders_ids = await get_ids_of_orders_with_status('Pending')
            await remove_pending_order_links_to_encumbrances_in_other_fy(pending_orders_ids, fiscal_year_id)
    elif choice == 7:
        open_orders_ids = await get_ids_of_orders_with_status('Open')
        await unrelease_open_orders_encumbrances_with_nonzero_amounts(fiscal_year_id, open_orders_ids)
    elif choice == 8:
        open_orders_ids = await get_ids_of_orders_with_status('Open')
        await release_open_orders_encumbrances_with_negative_amounts(fiscal_year_id, open_orders_ids)
    elif choice == 9:
        open_orders_ids = await get_ids_of_orders_with_status('Open')
        await release_cancelled_order_line_encumbrances(fiscal_year_id, open_orders_ids)
    elif choice == 10:
        closed_orders_ids = await get_ids_of_orders_with_status('Closed')
        open_orders_ids = await get_ids_of_orders_with_status('Open')
        open_and_closed_orders_ids = closed_orders_ids + open_orders_ids
        await recalculate_budget_encumbered(open_and_closed_orders_ids, fiscal_year_id)
    elif choice == 11:
        closed_orders_ids = await get_ids_of_orders_with_status('Closed')
        await release_unreleased_encumbrances_for_closed_orders(closed_orders_ids, fiscal_year_id)
    delta = round(time.time() - initial_time)
    hours, remainder = divmod(delta, 3600)
    minutes, seconds = divmod(remainder, 60)
    print(f'\nElapsed time: {hours} hour(s), {minutes} minute(s) and {seconds} second(s).')


def menu(fiscal_year_code):
    print('1) Run all fixes (can be long)')
    print('2) Remove duplicate encumbrances')
    print('3) Fix order line - encumbrance relations')
    print('4) Fix encumbrance order status for closed orders (current fiscal year only)')
    print('5) Fix encumbrance properties for open and pending orders (current fiscal year only)')
    print('6) Remove pending order links to encumbrances in previous fiscal years (current fiscal year only)')
    print('7) Unrelease open order encumbrances with nonzero amounts')
    print('8) Release open order encumbrances with negative amounts')
    print('9) Release cancelled order line encumbrances')
    print('10) Recalculate all budget encumbered amounts (avoid any transaction while this is running!)')
    print('11) Release unreleased encumbrances for closed orders')
    print('12) Quit')
    choice_s = input('Choose an option: ')
    try:
        choice_i = int(choice_s)
    except ValueError:
        print('Invalid option.')
        return
    if choice_i < 1 or choice_i > 12:
        print('Invalid option.')
        return
    if choice_i == 12:
        return
    if choice_i == 1 and dryrun:
        print("Note that, because dry-run mode is enabled, some operations will behave differently because they "
              "depend on the execution of previous ones, such as when recalculating the budget encumbrances.")
    asyncio.run(run_operation(choice_i, fiscal_year_code))


# ---------------------------------------------------
# Main

def main():
    global okapi_url, tenant, username

    if len(sys.argv) != 5:
        print("Syntax: ./fix_encumbrances.py 'fiscal_year_code' 'okapi_url' 'tenant' 'username'")
        raise SystemExit(1)
    fiscal_year_code = sys.argv[1]
    okapi_url = sys.argv[2]
    tenant = sys.argv[3]
    username = sys.argv[4]
    dryrun_mode_selection()
    menu(fiscal_year_code)


main()
