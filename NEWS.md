# 3.0.3
* [MODEUSHARV-56](https://issues.folio.org/browse/MODEUSHARV-56) Iris R1 2021 Log4j vulnerability

# 3.0.2
* [MODEUSHARV-36](https://issues.folio.org/browse/MODEUSHARV-36) Unable to parse item and database reports

# 3.0.1
* [MODEUSHARV-29](https://issues.folio.org/browse/MODEUSHARV-29) No reports created if ServiceEndpoint fails to initialize
* [MODEUSHARV-31](https://issues.folio.org/browse/MODEUSHARV-31) Exceptions while fetching unsupported reports
* [MODEUSHARV-32](https://issues.folio.org/browse/MODEUSHARV-32) Reports without report items should be considered as failed
* Bump guava from 26.0-jre to 29.0-jre in /mod-erm-usage-harvester-spi

# 3.0.0 (2021-03-17)
* Use ApiKey when connecting to Counter5 provider (MODEUSHARV-24)
* Increase-container-memory (MODEUSHARV-21)
* Upgrade to RMB 32 (MODEUSHARV-18)
* Add support for harvesting multiple months in a single request (MODEUSHARV-12)

# 2.0.2 (2020-11-04)
* Upgrade to RMB 31.1.5 and Vert.x 3.9.4 (MODEUSHARV-16)

# 2.0.1 (2020-10-23)
* Bugfix: Module logging error "ERROR StatusLogger Unrecognized format specifier" (MODEUSHARV-14)
* Upgrade to junit 4.13.1

# 2.0.0
* Upgrade to RAML Module Builder 31.x (MODEUSHARV-8)
* Update module to JDK 11 (MODEUSHARV-7)

# 1.8.0
* Set datetime of last harvesting attempt in UDP (MODEUSHARV-4)
* Make path for mod-configuration module configurable (MODEUSHARV-5)
* Fix potential security vulnerability CVE-2018-10237 (MODEUSHARV-6)

# 1.7.2
* Fix broken logging after log4j-core upgrade

# 1.7.1
* Bump quartz version to fix security vulnerability
* Bump log4j-core version to fix security vulnerability
* Query parameters are not encoded ([MODEUSHARV-1](https://issues.folio.org/browse/MODEUSHARV-2))
* Client closed error when harvesting through aggregator ([MODEUSHARV-2](https://issues.folio.org/browse/MODEUSHARV-1))

# 1.7.0
* Add permissionsRequired to ModuleDescriptor (MODEUS-72)
* Update RMB to v30.0.0 (MODEUS-57)

# 1.6.1
* Use single instance of WebClient to prevent unavailable backend while harvesting counter-reports (MODEUS-48) 

# 1.6.0
* Fix security vulnerability reported in log4j:log4j (MODEUS-46)
* Update required interface versions in ModuleDescriptor

# 1.5.0
* Update RMB to v29.1.0
* Use new base docker image && new JAVA_OPTIONS (FOLIO-2358)
* cs41: Improve logging, follow redirects (UIEUS-122)
* Update jackson-databind to v2.10.0
* Add default LaunchDescriptor settings (FOLIO-2235)

# 1.4.1
* cs41: Parallel harvesting of reports (MODEUS-34)
* cs41: Use `RequestorName` and `RequestorEmail` when creating a `ReportRequest` (UIEUS-109)

# 1.4.0
* Add periodic harvesting (UIEUS-3)
* Update jackson-databind version to 2.9.9.3
* Update RMB version to 26.2.4
* Remove unused schema (MODEUS-28)

# 1.3.2
* Update jackson-databind to 2.9.9.1 CVE-2019-12814

# 1.3.1
* core: Fix CQL for configuration module: Use `configName` instead of `code`
* cs41: Bump erm-usage-counter to 1.2.1 with NPE fix for missing error messages (UIEUS-75)
* core: Undeploy WorkerVerticle if providerId invalid
* core: Fix CQL strings
* cs50: Prettify error messages
* cs50: Dont store reports without reportHeader or with exceptions
* cs50: Fix handling of 201-299 status codes
* cs50: Fix Master Reports not getting pulled (UIEUS-84)

# 1.3.0
* Use mod-configuration for maxFailedAttempts setting
* Fix jackson-databind vulnerability
* Update models & test data

# 1.2.0
* Add COUNTER 5 implementation

# 1.1.0
* Evaluate `OKAPI_URL` environment variable
* Support harvesting via HTTP proxy
* Undeploy WorkerVerticle when processing for a tenant finishes
* Use modulePermissions instead of manual authentication
* Added RMB to project
* Added endpoint to start harvesting for a single usage data provider
* Added endpoint for listing available counter/sushi service implementations

# 1.0.0
* First release, moved from mod-erm-usage
