#!/usr/bin/env python

import requests
import getpass
import sys

ITEM_MAX = 2147483647


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


def get_fiscal_year_ids_by_query(query):
    params = {'query': query, 'offset': '0', 'limit': ITEM_MAX}
    try:
        r = requests.get(okapi_url + 'finance/fiscal-years', headers=headers, params=params)
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
        r = requests.get(okapi_url + 'orders/composite-orders', headers=headers, params=params)
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


def get_encumbrances_by_query(query):
    params = {'query': query, 'offset': '0', 'limit': ITEM_MAX}
    try:
        r = requests.get(okapi_url + 'finance/transactions', headers=headers, params=params)
        if r.status_code != 200:
            raise_exception_for_reply(r)
        encumbrances = r.json()['transactions']
    except Exception as err:
        print('Error getting encumbrances with query "{}": {}'.format(query, err))
        raise SystemExit(1)
    return encumbrances


def get_encumbrance_by_id(encumbrance_id):
    try:
        r = requests.get(okapi_url + 'finance/transactions/{}'.format(encumbrance_id), headers=headers)
        if r.status_code != 200:
            raise_exception_for_reply(r)
        return r.json()
    except Exception as err:
        print('Error getting encumbrance with id "{}": {}'.format(encumbrance_id, err))
        raise SystemExit(1)


def get_budgets_by_query(query):
    params = {'query': query, 'offset': '0', 'limit': ITEM_MAX}
    try:
        r = requests.get(okapi_url + 'finance/budgets', headers=headers, params=params)
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


def get_open_and_closed_orders_ids():
    query = 'workflowStatus=="Open" OR workflowStatus=="Closed"'
    open_and_closed_orders_ids = get_order_ids_by_query(query)
    print('  Open and closed orders:', len(open_and_closed_orders_ids))
    return open_and_closed_orders_ids


def put_encumbrance(encumbrance):
    try:
        url = '{}finance/encumbrances/{}'.format(okapi_url, encumbrance['id'])
        r = requests.put(url, headers=headers, json=encumbrance)
        if r.status_code != 204:
            raise_exception_for_reply(r)
    except Exception as err:
        print('Error putting encumbrance "{}": {}'.format(encumbrance, err))
        raise SystemExit(1)


def transaction_summary(order_id, num_transactions):
    data = {'id': order_id, 'numTransactions': num_transactions}
    try:
        url = '{}finance/order-transaction-summaries/{}'.format(okapi_url, order_id)
        r = requests.put(url, headers=headers, json=data)
        if r.status_code != 204:
            raise_exception_for_reply(r)
    except Exception as err:
        print('Error when setting the transaction summary for order "{}": {}'.format(order_id, err))
        raise SystemExit(1)


def get_budget_by_fund_id(fund_id, fiscal_year_id):
    query = 'fundId=={} AND fiscalYearId=={}'.format(fund_id, fiscal_year_id)
    budgets = get_budgets_by_query(query)
    if len(budgets) == 0:
        print('Could not find budget for fund "{}" and fiscal year "{}".'.format(fund_id, fiscal_year_id))
        raise SystemExit(1)
    return budgets[0]


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


def get_order_encumbrances(order_id, fiscal_year_id):
    query = 'encumbrance.sourcePurchaseOrderId=={} AND fiscalYearId=={}'.format(order_id, fiscal_year_id)
    return get_encumbrances_by_query(query)


# ---------------------------------------------------
# Fix encumbrance orderStatus for closed orders

def get_order_encumbrances_to_fix(order_id, fiscal_year_id):
    query = 'encumbrance.orderStatus<>"Closed" AND encumbrance.sourcePurchaseOrderId=={} AND fiscalYearId=={}'\
      .format(order_id, fiscal_year_id)
    return get_encumbrances_by_query(query)


def unrelease_order_encumbrances(order_id, encumbrances):
    transaction_summary(order_id, len(encumbrances))
    for encumbrance in encumbrances:
        encumbrance['encumbrance']['status'] = 'Unreleased'
        put_encumbrance(encumbrance)
    # the encumbrance amounts get modified (and the version in MG), so we need to get a fresh version
    modified_encumbrances = []
    for encumbrance in encumbrances:
        modified_encumbrances.append(get_encumbrance_by_id(encumbrance['id']))
    return modified_encumbrances


def fix_order_status_and_release_encumbrances(order_id, encumbrances):
    transaction_summary(order_id, len(encumbrances))
    for encumbrance in encumbrances:
        encumbrance['encumbrance']['status'] = 'Released'
        encumbrance['encumbrance']['orderStatus'] = 'Closed'
        put_encumbrance(encumbrance)


def fix_order_encumbrances_order_status(order_id, encumbrances):
    # We can't just PUT the encumbrance with a modified orderStatus, because
    # mod-finance-storage's EncumbranceService ignores changes to released encumbrances
    # unless it's to unrelease them. So we have to unrelease the encumbrances first.
    try:
        print('  Fixing {} encumbrance(s) for order {}...'.format(len(encumbrances), order_id))
        encumbrances = unrelease_order_encumbrances(order_id, encumbrances)
        fix_order_status_and_release_encumbrances(order_id, encumbrances)
    except Exception as err:
        print('Error when fixing order status in encumbrances for order {}:'.format(order_id), err)
        raise SystemExit(1)


def fix_encumbrance_order_status_for_closed_order(order_id, fiscal_year_id):
    encumbrances = get_order_encumbrances_to_fix(order_id, fiscal_year_id)
    if len(encumbrances) != 0:
        fix_order_encumbrances_order_status(order_id, encumbrances)
    return len(encumbrances)


def fix_encumbrance_order_status_for_closed_orders(closed_orders_ids, fiscal_year_id):
    print('Fixing encumbrance order status for closed orders...')
    nb_fixed_encumbrances = 0
    for order_id in closed_orders_ids:
        nb_fixed_encumbrances += fix_encumbrance_order_status_for_closed_order(order_id, fiscal_year_id)
    print('  Fixed order status for {} encumbrances.'.format(nb_fixed_encumbrances))
    return closed_orders_ids


# ---------------------------------------------------
# Unrelease open orders encumbrances with non-zero amounts

def get_released_order_encumbrances_with_non_zero_amounts(order_id, fiscal_year_id):
    query = ('amount<>0.0 AND encumbrance.status=="Released" AND encumbrance.sourcePurchaseOrderId=={} AND ' +
             'fiscalYearId=={}').format(order_id, fiscal_year_id)
    return get_encumbrances_by_query(query)


def unrelease_encumbrances(order_id, encumbrances):
    transaction_summary(order_id, len(encumbrances))
    for encumbrance in encumbrances:
        encumbrance['encumbrance']['status'] = 'Unreleased'
        put_encumbrance(encumbrance)


def unrelease_open_orders_encumbrances_with_nonzero_amounts(fiscal_year_id):
    print('Unreleasing open orders encumbrances with non-zero amounts...')
    open_orders_ids = get_open_orders_ids()
    nb_unreleased_encumbrances = 0
    for order_id in open_orders_ids:
        encumbrances_to_unrelease = get_released_order_encumbrances_with_non_zero_amounts(order_id, fiscal_year_id)
        if len(encumbrances_to_unrelease) != 0:
            unrelease_encumbrances(order_id, encumbrances_to_unrelease)
            nb_unreleased_encumbrances += len(encumbrances_to_unrelease)
    print('  Unreleased {} open orders encumbrances with non-zero amounts.'.format(nb_unreleased_encumbrances))
    return open_orders_ids


# ---------------------------------------------------
# Recalculate budget encumbered

def recalculate_budget_encumbered(fiscal_year_id):
    # Recalculate the encumbered property for all the budgets related to these encumbrances
    # Take closed orders into account because we might have to set a budget encumbered to 0
    print('Recalculating budget encumbered...')
    open_and_closed_orders_ids = get_open_and_closed_orders_ids()
    encumbered_for_fund = {}
    for order_id in open_and_closed_orders_ids:
        encumbrances = get_order_encumbrances(order_id, fiscal_year_id)
        for encumbrance in encumbrances:
            fund_id = encumbrance['fromFundId']
            if fund_id not in encumbered_for_fund:
                encumbered_for_fund[fund_id] = 0
            encumbered_for_fund[fund_id] += encumbrance['amount']
    print('  Updating budgets...')
    nb_modified = 0
    for fund_id, encumbered in encumbered_for_fund.items():
        budget = get_budget_by_fund_id(fund_id, fiscal_year_id)
        if budget['encumbered'] != encumbered:
            print('    Budget "{}": changing encumbered from {} to {}'
                  .format(budget['name'], budget['encumbered'], encumbered))
            budget['encumbered'] = encumbered
            put_budget(budget)
            nb_modified += 1
    print('  Edited {} budget(s).'.format(nb_modified))
    print('  Done recalculating budget encumbered.')


# ---------------------------------------------------
# Release unreleased encumbrances for closed orders

def release_encumbrances(order_id, encumbrances):
    transaction_summary(order_id, len(encumbrances))
    for encumbrance in encumbrances:
        encumbrance['encumbrance']['status'] = 'Released'
        put_encumbrance(encumbrance)


def get_order_encumbrances_to_release(order_id, fiscal_year_id):
    query = 'encumbrance.status=="Unreleased" AND encumbrance.sourcePurchaseOrderId=={} AND fiscalYearId=={}'\
      .format(order_id, fiscal_year_id)
    return get_encumbrances_by_query(query)


def release_order_encumbrances(order_id, fiscal_year_id):
    encumbrances = get_order_encumbrances_to_release(order_id, fiscal_year_id)
    if len(encumbrances) != 0:
        release_encumbrances(order_id, encumbrances)
    return len(encumbrances)


def release_unreleased_encumbrances_for_closed_orders(closed_orders_ids, fiscal_year_id):
    print('Releasing unreleased encumbrances for closed orders...')
    nb_released_encumbrances = 0
    for order_id in closed_orders_ids:
        nb_released_encumbrances += release_order_encumbrances(order_id, fiscal_year_id)
    print('  Released {} encumbrances.'.format(nb_released_encumbrances))


# ---------------------------------------------------
# Main

def main():
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
    fiscal_year_id = get_fiscal_year_id(fiscal_year_code)
    closed_orders_ids = get_closed_orders_ids()
    fix_encumbrance_order_status_for_closed_orders(closed_orders_ids, fiscal_year_id)
    unrelease_open_orders_encumbrances_with_nonzero_amounts(fiscal_year_id)
    recalculate_budget_encumbered(fiscal_year_id)
    release_unreleased_encumbrances_for_closed_orders(closed_orders_ids, fiscal_year_id)
    print('Done.')


okapi_url = ''
headers = {}
main()
