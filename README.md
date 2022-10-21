# mod-finance-storage

Copyright (C) 2017-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

This RMB-module is responsible for the persistence of finance-related data (i.e. funds, ledgers, transactions, etc.)

For additional information regarding this module, please refer to the [Finance Module WIKI](https://wiki.folio.org/display/RM/Acquisitions+Fund+Module).


## Building the Project

To compile this module, head to the root-folder and run the following command in your Terminal:

```
mvn clean install
```

To run the module in standalone mode (i.e. without involving Okapi):
```
DB_HOST=localhost DB_PORT=5432 DB_USERNAME=myuser DB_PASSWORD=mypass DB_DATABASE=mydb \
 java -jar target/mod-finance-fat.jar -Dhttp.port=8081
```

## Issue tracker

See project [MODFISTO](https://issues.folio.org/browse/MODFISTO)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

## ModuleDescriptor

See the [ModuleDescriptor](descriptors/ModuleDescriptor-template.json)
for the interfaces that this module requires and provides, the permissions,
and the additional module metadata.

## API Documentation

Generated [API documentation](https://dev.folio.org/reference/api/#mod-finance-storage).

## Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-finance-storage).

## Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-finance-storage/).

