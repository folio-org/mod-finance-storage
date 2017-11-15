# MOD-SAMPLE

Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the file "LICENSE" for more information.

#Introduction

This module is responsible for the persistence of People data.

This is an RMB-based module.


For additional information please refer to the [WIKI](https://wiki.folio.org).


For API documentation, run this project locally and then go to [http://localhost:8081/apidocs/index.html?raml=raml/person.raml](http://localhost:8081/apidocs/index.html?raml=raml/person.raml)



Mod-Sample leverages RMB to build the code.

The database connection must be configured in the following file:

```
src/main/resources/postgres-conf.json
```

The DB schema is defined in  
```
src/main/resources/templates/schema.json
```

Deploying the module in OKAPI should initialize the schema. Nonetheless, for testing purposes, the following files have been included to build the DB schema from scratch:

```
src/main/resources/create_tenant.sql
src/main/resources/delete_tenant.sql
```

