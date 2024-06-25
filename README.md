# mod-erm-usage-harvester

Copyright (C) 2018-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0. See the
file "[LICENSE](LICENSE)" for more information.

![Development funded by European Regional Development Fund (EFRE)](assets/EFRE_2015_quer_RGB_klein.jpg)

## Introduction

Module for harvesting counter reports.

## Requirements

* The module needs to know about the Okapi URL ([see here](#setting-the-okapi-url)).
* For scheduled harvesting you need to provide user credentials ([see here](#periodic-harvesting)).
* Environment variables for database connectivity need to be
  provided ([see here](https://github.com/folio-org/raml-module-builder#environment-variables)).

## Installation

```
$ git clone ...
$ cd mod-erm-usage-harvester
$ mvn clean install
```

### Run plain jar

```
$ env OKAPI_URL=http://127.0.0.1:9130 java -jar \
  mod-erm-usage-harvester-bundle/target/mod-erm-usage-harvester-bundle-fat.jar
```

### Run via Docker

#### Build docker image

```
$ docker build -t mod-erm-usage-harvester .
```

#### Run docker image

```
$ docker run -e OKAPI_URL=http://127.0.0.1:9130 -p 8081:8081 mod-erm-usage-harvester
```

## Configuration

### Listening port

The default listening port is `8081` and can be set by using `-Dhttp.port` parameter when running
the jar file or using the `-p` flag when using `docker run`.

### Setting the Okapi URL

Use the environment variable named `OKAPI_URL` to provide the URL to Okapi.

### Proxy configuration

Proxy settings are configured via JVM system properties if you are running the plain jar.

* `http.proxyHost`, `http.proxyPort`, `https.proxyHost`, `https.proxyPort`, `http.nonProxyHosts`

And via environment variables if you are running the Docker container.

* `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY`  
  These get translated into JVM system properties by
  the [base image](https://github.com/folio-org/folio-tools/tree/master/folio-java-docker/openjdk17).

### Quartz scheduler

Quartz configuration is located
in [quartz.properties](mod-erm-usage-harvester-bundle/src/main/resources/org/quartz/quartz.properties)
. If you wish to use another file, you must define the system property `org.quartz.properties` to
point to the file you want. You can also set individual quartz properties using system properties (
e.g. `-Dorg.quartz.threadPool.threadCount=8`). The `org.quartz.threadPool.threadCount` 
property controls how many providers are harvested concurrently.

### Hazelcast

The default Quartz configuration uses the `HazelcastJobStore` for clustering which relies on 
Hazelcast. By default the [standard configuration](https://github.com/hazelcast/hazelcast/blob/master/hazelcast/src/main/resources/hazelcast-default.xml)
shipped with hazelcast is used. You can supply your own XML or YAML configuration through the 
`hazelcast.config` system property or just put it into the working directory. If you're using 
clustering, make sure that member discovery is working by inspecting the logs. You might want to 
tailor the Hazelcast configuration to suit your particular deployment environment. You can read
about Hazelcast discovery mechanisms [here](https://docs.hazelcast.com/hazelcast/5.3/clusters/discovery-mechanisms).

## Periodic harvesting

Periodic harvesting requires the module to login as a user. User credentials are set separately for
each tenant through the environment variables `{tenant}_USER_NAME` and `{tenant}_USER_PASS`,
where `{tenant}` is a placeholder for the tenant id. The user needs to have
the `ermusageharvester.start` permission.

Example:

```
DIKU_USER_NAME=mod-erm-usage-harvester
DIKU_USER_PASS=password123
```

Periodic harvesting is set up through the `erm-usage-harvester/periodic` API. Configuration is done
for each tenant separately by using the `X-Okapi-Tenant` header.
See [PeriodicConfig](ramls/schemas/periodicConfig.json)
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

This request will create a schedule which triggers harvesting for tenant `diku` each day at 8am UTC
starting on `2019-01-01`.

__Note:__ Using `"periodicInterval: "monthly"`  and `startAt` with days > 28 will result in a _'last
day of month'_ schedule.

Example 2:

```json
{
  "startAt": "2019-01-29T08:00:00.000+0000",
  "periodicInterval": "monthly"
}
```

This configuration will trigger harvesting every last day of month at 8am UTC starting
on `2019-01-31`
followed by `2019-02-28`, `2019-03-31`, `2019-04-30`, ... .

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

#### Request parameters

To enable the creation of standard views, master reports are retrieved with the following additional parameters:

| Report | Attributes_To_Show                                                                     | Include_Parent_Details |
| ------ | -------------------------------------------------------------------------------------- | ---------------------- |
| DR     | Data_Type\|Access_Method                                                               |                        |
| IR     | Authors\|Publication_Date\|Article_Version\|Data_Type\|YOP\|Access_Type\|Access_Method | True                   |
| PR     | Data_Type\|Access_Method                                                               |                        |
| TR     | Data_Type\|Section_Type\|YOP\|Access_Type\|Access_Method                               |                        |

_Example:_  
`/reports/dr?requestor_id=xxx&customer_id=xxx&begin_date=2021-01&end_date=2021-12&attributes_to_show=Data_Type|Access_Method`

#### Additional processing

Due to providers responding in various ways the provider response is intercepted and adjusted before processing.  
This is nescessary as some providers use `2xx` status codes to send sushi errors, but the generated client expects `2xx` codes to return counter reports and different codes to return sushi errors.  
So if reponses with status code `2xx` are received, it is checked whether the response data structure matches one of the 4 counter master reports (`TR`, `PR`, `DR` and `IR`). If it does match, no changes are made to the response. If it does not match, the response gets transformed into a `400 - Bad Request` response, preserving the original response body in cases listed below.

Some observations and how they are handled so far:

* Providers use `2xx` status codes to return sushi errors, not reports (gets routed and handled as `400` with original response body)
* Providers return sushi errors as array instead of object (array makes it into the response body)
* Providers return `"null"` instead of sushi error (returns a `InvalidReportException: null`)
* Providers return reports with a `Report_Header` that contains a `Exception` object instead of a `Exceptions` array (not handled, will be interpreted as report without `Exceptions`)

## Additional information

### Issue tracker

See project [MODEUSHARV](https://issues.folio.org/browse/MODEUSHARV)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described, with further FOLIO
Developer documentation at [dev.folio.org](https://dev.folio.org/)
