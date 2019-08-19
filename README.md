# mod-erm-usage-harvester

Copyright (C) 2018-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction
Module for harvesting counter reports.

## Requirements
Module needs to know about the Okapi URL ([see here](#setting-the-okapi-url)).

## Installation

```
$ git clone ...
$ cd mod-erm-usage-harvester
$ mvn clean install
```

### Run plain jar
```
$ cd mod-erm-usage-harvester-bundle
$ java -jar target/mod-erm-usage-harvester-bundle-fat.jar -conf target/config.json
```

### Run via Docker

#### Build docker image
```
$ docker build -t mod-erm-usage-harvester .
```

#### Run docker image
```
$ docker run -p 8081:8081 mod-erm-usage-harvester .
```

#### Pass configuration to docker container
as JSON string
```
$ docker run -e 'CONFIG={"okapiUrl": "http://172.17.0.1:9130"}' mod-erm-usage-harvester
```
or from file
```
$ docker run -e "CONFIG=$(<config.json)" mod-erm-usage-harvester
```

## Configuration
Configuration is done via JSON file
```json
{
  "okapiUrl": "http://localhost:9130",
  "tenantsPath": "/_/proxy/tenants",
  "reportsPath": "/counter-reports",
  "providerPath": "/usage-data-providers",
  "aggregatorPath": "/aggregator-settings"
}
```
A [default configuration](mod-erm-usage-harvester-bundle/config-template.json) is read from `config.json` in the execution directory. It can be overwritten by using the `-conf` parameter or setting the `CONFIG` environment variable.

The default listening port is `8081` and can be set by using `-Dhttp.port` parameter.

### Setting the Okapi URL
..is done either by configuration file like above, or by environment variable named `OKAPI_URL`.

### Proxy configuration
Proxy settings are configured via JVM system properties.
* `http.proxyHost`, `http.proxyPort`, `https.proxyHost`, `https.proxyPort`, `http.nonProxyHosts`

If running the Docker container use environment variables. These get translated into system properties by `run-java.sh`.
* `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY`

## Periodic harvesting
Module can be set up to start harvesting regularly at defined intervals through the `erm-usage-harvester/periodic` API. Configuration is done for each tenant separately by using the `X-Okapi-Tenant` header.
See [PeriodicConfig](ramls/schemas/periodicConfig.json) and [periodic.raml](ramls/periodic.raml).

Example:
```
curl --request POST \
  --url http://localhost:9130/erm-usage-harvester/periodic \
  --header 'content-type: application/json' \
  --header 'x-okapi-tenant: diku' \
  --data '{
  "startAt": "2019-01-01T08:00:00.000+0000",
  "periodicInterval": "daily"
}'
```
Will create a schedule which triggers harvesting for tenant `diku`  each day at 8am UTC starting on `2019-01-01`.

__Note:__ Using `"periodicInterval: "monthly"`  and `startAt` with days > 28 will result in a _'last day of month'_ schedule.

Example 2:
```json
{
  "startAt": "2019-01-29T08:00:00.000+0000",
  "periodicInterval": "monthly"
}
```
Will trigger harvesting every last day of month at 8am UTC starting on `2019-01-31` followed by `2019-02-28`, `2019-03-31`, `2019-04-30`, ... .

## Additional information

### Issue tracker

See project [MODERM](https://issues.folio.org/browse/MODERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

