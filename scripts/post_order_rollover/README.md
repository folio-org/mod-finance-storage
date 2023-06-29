# Post order rollover

This script parses logs that contain "Exception calling PUT /orders-storage/po-lines" lines to retrieve order lines state that was failed to save.

## Running the script :

- Use a recent version of node.js (at least 16)
- Copy/Create logs file (in .txt format) in the script location


### Script env variable :

- Okapi URL (OKAPI_HOST)
- Tenant (OKAPI_TENANT)
- Username (OKAPI_USERNAME)
- User password (OKAPI_PASSWORD)
- Logs file name (ORDER_LOGS)

### Execution example :
```
cp /path/to/logs/logs.txt .

export OKAPI_HOST=folio-snapshot-okapi.dev.folio.org
export OKAPI_TENANT=diku
export ORDER_LOGS=logs.txt
export OKAPI_USERNAME=diku_admin
export OKAPI_PASSWORD=admin
```

`node ./post_order_rollover.js`

To save output in a file, use `tee` on Linux:\
`node ./post_order_rollover.js | tee my_latest_run.log`

### Required permissions for the user
These can be set with a permission group created with the API.

- `orders-storage.po-lines.item.put`
