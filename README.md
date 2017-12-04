# mod-finance

Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the file "LICENSE" for more information.

# Introduction

This RMB-module is responsible for the persistence of finance-related data (i.e. funds, ledgers, transactions, etc.)

For additional information regarding this module, please refer to the [Finance Module WIKI](https://wiki.folio.org/display/RM/Acquisitions+Fund+Module).


## Building the Project

To compile this module, head to the root-folder and run the following command in your Terminal:

```
mvn clean install
```

To run the module in standalone mode (i.e. without involving Okapi):
```
java -jar target/mod-finance-fat.jar -Dhttp.port=8081 embed_postgres=true
```

>Note that the above command launches an embedded Postgres server and is accessible using the default creds found in the *Credentials* section [here](https://github.com/folio-org/raml-module-builder).

## API Documentation

When running in standalone mode, you may access the module's API docs through the following links: 
* [Budgets](http://localhost:8081/apidocs/index.html?raml=raml/budget.raml)
* [Fiscal Year](http://localhost:8081/apidocs/index.html?raml=raml/fiscal_year.raml)
* [Fund Distribution](http://localhost:8081/apidocs/index.html?raml=raml/fund_distribution.raml)
* [Funds](http://localhost:8081/apidocs/index.html?raml=raml/funds.raml)
* [Ledgers](http://localhost:8081/apidocs/index.html?raml=raml/ledger.raml)
* [Tags](http://localhost:8081/apidocs/index.html?raml=raml/tag.raml)
* [Transaction](http://localhost:8081/apidocs/index.html?raml=raml/transaction.raml)
