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

## Additional information

### Issue tracker

See project [MODERM](https://issues.folio.org/browse/MODERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

