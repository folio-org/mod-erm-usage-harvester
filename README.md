# mod-erm-usage-harvester

Copyright (C) 2018-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.


## Installation

```
$ git clone ...
$ cd mod-erm-usage-harvester
$ mvn clean install
```

## Run plain jar

```
$ cd mod-erm-usage-harvester-core
$ java -jar target/mod-erm-usage-harvester-core-fat.jar -conf target/config.json
```

## Run via Docker

### Build docker image
```
$ docker build -t mod-erm-usage-harvester .
```

### Run docker image
```
$ docker run -p 8081:8081 mod-erm-usage-harvester .
```

### Register ModuleDescriptor

```
$ cd target
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d @ModuleDescriptor.json http://localhost:9130/_/proxy/modules
```

### Activate module for tenant (do this before registering DeploymentDescriptor)

```
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d '{ "id": "mod-erm-usage-harvester-1.0.0"}' http://localhost:9130/_/proxy/tenants/diku/modules
```

### Register DeploymentDescriptor

Change _nodeId_ in _DockerDeploymentDescriptor.json_ to e.g. your hosts IP address (e.g. 10.0.2.15). Then execute:

```
$ curl -w '\n' -X POST -D - -H "Content-type: application/json" -d @DockerDeploymentDescriptor.json http://localhost:9130/_/discovery/modules
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
  "moduleIds": [
    "mod-erm-usage-1.0.0",
    "mod-erm-usage-harvester-1.0.0"
  ],
  "loginPath": "/bl-users/login",
  "requiredPerm": "ermusage.all"
}
```
A [default configuration](mod-erm-usage-harvester-core/config-template.json) is read from `config.json` in the execution directory. It can be overridden by using the `-conf` parameter or setting the `CONFIG` environment variable.

The default listening port is `8081` and can be set by using `-Dhttp.port` parameter.

### Pass configuration to docker container

pass as JSON string
```
$ docker run -e 'CONFIG={"okapiUrl": "http://172.17.0.1:9130"}' mod-erm-usage-harvester
```

or from file
```
$ docker run -e "CONFIG=$(<config.json)" mod-erm-usage-harvester
```

### Requirements

Module needs user `diku_admin` to have `ermusage.all` permission for harvesting to work.

## Additional information

### Issue tracker

See project [MODERM](https://issues.folio.org/browse/MODERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

