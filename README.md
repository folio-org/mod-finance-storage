# mod-finance-storage

Copyright (C) 2017-2023 The Open Library Foundation

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

### Secure storage environment variables

#### AWS-SSM

Required when `SECRET_STORE_TYPE=AWS_SSM`

| Name                                          | Default value | Description                                                                                                                                                    |
|:----------------------------------------------|:--------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SECRET_STORE_AWS_SSM_REGION                   | -             | The AWS region to pass to the AWS SSM Client Builder. If not set, the AWS Default Region Provider Chain is used to determine which region to use.              |
| SECRET_STORE_AWS_SSM_USE_IAM                  | true          | If true, will rely on the current IAM role for authorization instead of explicitly providing AWS credentials (access_key/secret_key)                           |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_ENDPOINT | -             | The HTTP endpoint to use for retrieving AWS credentials. This is ignored if useIAM is true                                                                     |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_PATH     | -             | The path component of the credentials endpoint URI. This value is appended to the credentials endpoint to form the URI from which credentials can be obtained. |

#### Vault

Required when `SECRET_STORE_STORE_TYPE=VAULT`

| Name                                    | Default value | Description                                                                         |
|:----------------------------------------|:--------------|:------------------------------------------------------------------------------------|
| SECRET_STORE_VAULT_TOKEN                | -             | token for accessing vault, may be a root token                                      |
| SECRET_STORE_VAULT_ADDRESS              | -             | the address of your vault                                                           |
| SECRET_STORE_VAULT_ENABLE_SSL           | false         | whether or not to use SSL                                                           |
| SECRET_STORE_VAULT_PEM_FILE_PATH        | -             | the path to an X.509 certificate in unencrypted PEM format, using UTF-8 encoding    |
| SECRET_STORE_VAULT_KEYSTORE_PASSWORD    | -             | the password used to access the JKS keystore (optional)                             |
| SECRET_STORE_VAULT_KEYSTORE_FILE_PATH   | -             | the path to a JKS keystore file containing a client cert and private key            |
| SECRET_STORE_VAULT_TRUSTSTORE_FILE_PATH | -             | the path to a JKS truststore file containing Vault server certs that can be trusted |

#### FSSP (Folio Secure Store Proxy)

Required when `SECRET_STORE_TYPE=FSSP`

| Name                                   | Default value         | Description                                 |
|:---------------------------------------|:----------------------|:--------------------------------------------|
| SECRET_STORE_FSSP_ADDRESS              | -                     | The address (URL) of the FSSP service.      |
| SECRET_STORE_FSSP_SECRET_PATH          | secure-store/entries  | The path in FSSP where secrets are stored.  |
| SECRET_STORE_FSSP_ENABLE_SSL           | false                 | Whether to use SSL for the FSSP connection. |
| SECRET_STORE_FSSP_TRUSTSTORE_PATH      | -                     | Truststore file path for SSL connections.   |
| SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE | -                     | Truststore file type (e.g., JKS, PKCS12).   |
| SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD  | -                     | Truststore password for SSL connections.    |

## Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-finance-storage).

## Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-finance-storage/).

