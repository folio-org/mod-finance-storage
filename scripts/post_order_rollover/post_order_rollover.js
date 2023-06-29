const fs = require('fs');
const readline = require('readline');
const https = require('https');

const filePath = process.env.ORDER_LOGS;
const OKAPI = process.env.OKAPI_HOST;
const TENANT = process.env.OKAPI_TENANT;

const auth = {};

const doRequest = (options, data) => {
  return new Promise((resolve, reject) => {
    const request = https.request(options, (response) => {
      let responseData = '';

      response.on('data', (chunk) => {
        responseData += chunk;
      });

      response.on('end', () => {
        if (response.statusCode >= 400) {
          reject(responseData);
        } else {
          resolve(response.headers);
        }
      });
    });

    request.on('error', (error) => {
      console.error('Error:', error);

      reject();
    });

    if (data) {
      request.write(JSON.stringify(data));
    }

    request.end();
  });
};

const login = async (okapi, tenant, username, password) => {
  console.log('----- Login ----');
  const headers = await doRequest({
    hostname: okapi,
    port: 443,
    path: '/bl-users/login',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-okapi-tenant': tenant,
    }
  }, { username, password });

  auth.token = headers['x-okapi-token'];

  console.log('----- Login successful ----');
};

const updateOrderLine = async (okapi, tenant, orderLine) => {
  const { id } = orderLine;

  try {
    console.log('\n Attempting to save order line:', orderLine.id);

    await doRequest({
      hostname: okapi,
      port: 443,
      path: `/orders-storage/po-lines/${id}`,
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'x-okapi-tenant': tenant,
        'x-okapi-token': auth.token,
      }
    }, orderLine);

    console.log('Order lines saved', orderLine.id);
  } catch (e) {
    console.log(`order line ${id} wasn't saved`, e);
  }
};

const parseOrderLine = (log) => {
  const startIdx = log.indexOf('{');
  const endIdx = log.lastIndexOf('}');
  const jsonSubstring = log.substring(startIdx, endIdx + 1);

  try {
    return JSON.parse(
      jsonSubstring.includes('""id""')
        ? jsonSubstring.replace(/""(.*?)""/g, '"$1"')
        : jsonSubstring
    );
  } catch (error) {
    console.error('Invalid JSON:', jsonSubstring);

    return null;
  }
}

let sequencePromise = Promise.resolve();

sequencePromise = sequencePromise.then(() => login(OKAPI, TENANT, process.env.OKAPI_USERNAME, process.env.OKAPI_PASSWORD));

const readInterface = readline.createInterface({
  input: fs.createReadStream(filePath),
  console: false,
});

readInterface.on('line', function(line) {
  if (line && line.includes('Exception calling PUT /orders-storage/po-lines')) {
    const orderLine = parseOrderLine(line);

    if (orderLine) {
      sequencePromise = sequencePromise.then(() => updateOrderLine(OKAPI, TENANT, orderLine));
    }
  }
});

readInterface.on('close', () => {});
