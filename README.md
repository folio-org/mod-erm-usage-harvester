# mod-erm-usage-harvester

Copyright (C) 2018-2021 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the
file "[LICENSE](LICENSE)" for more information.

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
  "aggregatorPath": "/aggregator-settings",
  "modConfigurationPath": "/configurations/entries"
}
```

A [default configuration](mod-erm-usage-harvester-bundle/config-template.json) is read
from `config.json` in the execution directory. It can be overwritten by using the `-conf` parameter
or setting the `CONFIG` environment variable.

The default listening port is `8081` and can be set by using `-Dhttp.port` parameter.

### Setting the Okapi URL

..is done either by configuration file like above, or by environment variable named `OKAPI_URL`.

### Proxy configuration

Proxy settings are configured via JVM system properties.

* `http.proxyHost`, `http.proxyPort`, `https.proxyHost`, `https.proxyPort`, `http.nonProxyHosts`

If running the Docker container use environment variables. These get translated into system
properties by `run-java.sh`.

* `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY`

## Periodic harvesting

Module can be set up to start harvesting regularly at defined intervals through
the `erm-usage-harvester/periodic` API. Configuration is done for each tenant separately by using
the `X-Okapi-Tenant` header. See [PeriodicConfig](ramls/schemas/periodicConfig.json)
and [periodic.raml](ramls/periodic.raml).

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

Will create a schedule which triggers harvesting for tenant `diku`  each day at 8am UTC starting
on `2019-01-01`.

__Note:__ Using `"periodicInterval: "monthly"`  and `startAt` with days > 28 will result in a _'last
day of month'_ schedule.

Example 2:

```json
{
  "startAt": "2019-01-29T08:00:00.000+0000",
  "periodicInterval": "monthly"
}
```

Will trigger harvesting every last day of month at 8am UTC starting on `2019-01-31` followed
by `2019-02-28`, `2019-03-31`, `2019-04-30`, ... .

## ServiceEndpoint implementations

The [ServiceEndpoint](mod-erm-usage-harvester-spi/src/main/java/org/olf/erm/usage/harvester/endpoints/ServiceEndpoint.java)
implementation defines how reports are fetched for a provider. To provide additional implementations
you will need to implement the
[ServiceEndpointProvider](mod-erm-usage-harvester-spi/src/main/java/org/olf/erm/usage/harvester/endpoints/ServiceEndpointProvider.java)
interface and make it available on the classpath.

So far 3 implementations are provided:

* `mod-erm-usage-harvester-cs41`
  – [Counter Sushi 4.1](https://www.projectcounter.org/code-of-practice-sections/sushi/)
* `mod-erm-usage-harvester-cs50`
  – [Counter Sushi 5.0 API](https://app.swaggerhub.com/apis/COUNTER/counter-sushi_5_0_api/1.0.0)
* `mod-erm-usage-harvester-nss` – [Germanys National Statistics Server](https://statistik.hebis.de/)

Implementations available at runtime can be listed at `/erm-usage-harvester/impl`.

```
{
  "implementations": [
    {
      "name": "Counter-Sushi 4.1",
      "description": "SOAP-based implementation for CounterSushi 4.1",
      "type": "cs41",
      "isAggregator": false
    },
    {
      "name": "Counter-Sushi 5.0",
      "description": "Implementation for Counter/Sushi 5",
      "type": "cs50",
      "isAggregator": false
    },
    {
      "name": "Nationaler Statistikserver",
      "description": "Implementation for Germanys National Statistics Server (https://sushi.redi-bw.de).",
      "type": "NSS",
      "isAggregator": true,
      "configurationParameters": [
        "apiKey",
        "requestorId",
        "customerId",
        "reportRelease"
      ]
    }
  ]
}
```

### mod-erm-usage-harvester-cs50

Due to providers responding in various ways the provider response is intercepted and adjusted before
processing.  
This is nescessary as some providers use `2xx` status codes to send sushi errors, but the generated
client expects `2xx` codes to return counter reports and different codes to return sushi errors.  
So if reponses with status code `2xx` are received, it is checked whether the response data
structure matches one of the 4 counter master reports (`TR`, `PR`, `DR` and `IR`). If it does match,
no changes are made to the response. If it does not match, the response gets transformed into
a `400 - Bad Request` response, preserving the original response body.

Some observations and how they are handled so far:

* Providers use `2xx` status codes to return sushi errors (everything thats not a report gets routed
  to and handled as `400`)
* Providers return sushi errors as array instead of object (array makes it into the error message)
* Providers return `"null"` instead of sushi error (returns a `InvalidReportException: null`)
* Providers return reports with a `Report_Header` that contains a `Exception` object instead of
  a `Exceptions` array (not handled, will be interpreted as report without `Exceptions`)

## Additional information

### Issue tracker

See project [MODEUSHARV](https://issues.folio.org/browse/MODEUSHARV)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described, with further FOLIO
Developer documentation at [dev.folio.org](https://dev.folio.org/)

