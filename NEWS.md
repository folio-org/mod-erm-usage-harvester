# 5.0.2
* [MODEUSHARV-128](https://folio-org.atlassian.net/browse/MODEUSHARV-128) Add missing `okapi` interface to module descriptor

# 5.0.1
* [MODEUSHARV-125](https://folio-org.atlassian.net/browse/MODEUSHARV-125) Add missing `configuration` interface to module descriptor

# 5.0.0
* [EUREKA-288](https://folio-org.atlassian.net/browse/EUREKA-288) mod-erm-usage-harvester - add support for disabling system user creation/mgmt/use
* [MODEUSHARV-109](https://folio-org.atlassian.net/browse/MODEUSHARV-109) Vert.x 4.5.8, hazelcast 5.3.5, drop hazelcast-aws/hazelcast-kubernetes
* [MODEUSHARV-110](https://folio-org.atlassian.net/browse/MODEUSHARV-110) Review and cleanup Module Descriptor for mod-erm-usage-harvester
* [MODEUSHARV-111](https://folio-org.atlassian.net/browse/MODEUSHARV-111) Update to usage-data-providers 3.0
* [MODEUSHARV-112](https://folio-org.atlassian.net/browse/MODEUSHARV-112) Fail with error message when SYSTEM_USER_ENABLED=false
* [MODEUSHARV-115](https://folio-org.atlassian.net/browse/MODEUSHARV-115) Add support for counter-reports interface 4.0
* [MODEUSHARV-116](https://folio-org.atlassian.net/browse/MODEUSHARV-116) Add support for aggregator-settings 2.0
* [MODEUSHARV-118](https://folio-org.atlassian.net/browse/MODEUSHARV-118) Update 'Periodic harvesting' section in README
* [MODEUSHARV-119](https://folio-org.atlassian.net/browse/MODEUSHARV-119) Upgrade RMB to v35.3.0
* [MODEUSHARV-120](https://folio-org.atlassian.net/browse/MODEUSHARV-120) Adjust API parameters of client implementations

# 4.5.0
* [MODEUSHARV-107](https://folio-org.atlassian.net/browse/MODEUSHARV-107) Upgrade RMB to v35.2.0

# 4.4.1
* [MODEUSHARV-106](https://issues.folio.org/browse/MODEUSHARV-106) Update module dependencies, RMB v35.1.1, Vert.x v4.4.6
* [MODEUSHARV-104](https://issues.folio.org/browse/MODEUSHARV-104) Health check not configured in Jenkins pipeline
* [MODEUSHARV-103](https://issues.folio.org/browse/MODEUSHARV-103) OKAPI_URL environment variable not set in Dockerfile
* [MODEUSHARV-102](https://issues.folio.org/browse/MODEUSHARV-102) Cease harvesting process when concurrent report uploads fail
* [MODEUSHARV-101](https://issues.folio.org/browse/MODEUSHARV-101) Concurrency limit during harvesting is not honored
* [MODEUSHARV-100](https://issues.folio.org/browse/MODEUSHARV-100) High resource consumption when fetching TR reports for multi-month periods

# 4.4.0
* [MODEUSHARV-98](https://issues.folio.org/browse/MODEUSHARV-98) RMB v35.1.x update
* [MODEUSHARV-97](https://issues.folio.org/browse/MODEUSHARV-97) Remove configuration file
* [MODEUSHARV-96](https://issues.folio.org/browse/MODEUSHARV-96) Update env vars in ModuleDescriptor
* [MODEUSHARV-94](https://issues.folio.org/browse/MODEUSHARV-94) Update to Java 17
* [MODEUSHARV-92](https://issues.folio.org/browse/MODEUSHARV-92) Implement a timer interface that periodically runs cleanup tasks
* [MODEUSHARV-87](https://issues.folio.org/browse/MODEUSHARV-87) Add endpoint to delete finished harvesting jobs
* [MODEUSHARV-86](https://issues.folio.org/browse/MODEUSHARV-86) Add endpoint to update stale JobInfo objects
* [MODEUSHARV-85](https://issues.folio.org/browse/MODEUSHARV-85) Extend JobInfo with job execution result
* [MODEUSHARV-84](https://issues.folio.org/browse/MODEUSHARV-84) Upgrade counter dependency to v3
* [MODEUSHARV-77](https://issues.folio.org/browse/MODEUSHARV-77) Implement refresh token rotation

# 4.3.1
* Bump guava from 31.1-jre to 32.0.1-jre
* [MODEUSHARV-95](https://issues.folio.org/browse/MODEUSHARV-95) Error parsing DataTypeEnum from counter5 report

# 4.3.0
* [MODEUSHARV-82](https://issues.folio.org/browse/MODEUSHARV-82) RMB v35.0.6, Vert.x v4.3.8
* [MODEUSHARV-81](https://issues.folio.org/browse/MODEUSHARV-81) Return error message if report deserialization fails

# 4.2.1
* [MODEUSHARV-78](https://issues.folio.org/browse/MODEUSHARV-78) Hazelcast 4.2.6 fixing improper authentication CVE-2022-36437, RMB 35.0.4, Vert.x 4.3.7

# 4.2.0
* Reduce error output during tests
* Pin jackson-databind to v2.13.4.2
* [MODEUSHARV-75](https://issues.folio.org/browse/MODEUSHARV-75) Upgrade to RMB v35
* [MODEUSHARV-67](https://issues.folio.org/browse/MODEUSHARV-67) Provide information about harvesting jobs
* [MODEUSHARV-66](https://issues.folio.org/browse/MODEUSHARV-66) Support horizontal scaling

# 4.1.0
* [MODEUSHARV-73](https://issues.folio.org/browse/MODEUSHARV-73) Requests only using one authentication parameter
* [MODEUSHARV-72](https://issues.folio.org/browse/MODEUSHARV-72) RMB v34 upgrade - Morning Glory 2022 R2 module release
* [MODEUSHARV-71](https://issues.folio.org/browse/MODEUSHARV-71) Reports without report items are considered invalid
* [MODEUSHARV-70](https://issues.folio.org/browse/MODEUSHARV-70) CS50Exception: Split report size not equal to 1
* [MODEUSHARV-69](https://issues.folio.org/browse/MODEUSHARV-69) Update dependencies
* [MODEUSHARV-68](https://issues.folio.org/browse/MODEUSHARV-68) cs50 slf4j-ext Deserialization of Untrusted Data (CVE-2018-8088)
* [MODEUSHARV-63](https://issues.folio.org/browse/MODEUSHARV-63) Refactoring

# 4.0.1
* [MODEUSHARV-62](https://issues.folio.org/browse/MODEUSHARV-62) NPE in SchedulingUtil
* [MODEUSHARV-64](https://issues.folio.org/browse/MODEUSHARV-64) Sequential requests for sushi5.scholarlyiq.com

# 4.0.0
* [MODEUSHARV-47](https://issues.folio.org/browse/MODEUSHARV-47) Endpoint w/o required permissions
* [MODEUSHARV-50](https://issues.folio.org/browse/MODEUSHARV-50) Use system user for scheduled harvesting
* [MODEUSHARV-51](https://issues.folio.org/browse/MODEUSHARV-51) Cleanup unused code artifacts
* [MODEUSHARV-52](https://issues.folio.org/browse/MODEUSHARV-52) Scheduled harvesting triggers may execute twice
* [MODEUSHARV-53](https://issues.folio.org/browse/MODEUSHARV-53) RMB 33.2.1 fixing remote execution (CVE-2021-44228)
* [MODEUSHARV-54](https://issues.folio.org/browse/MODEUSHARV-54) Kiwi R3 2021 - Log4j vulnerability verification and correction
* [MODEUSHARV-55](https://issues.folio.org/browse/MODEUSHARV-55) Use correct values for attributes_to_show
* [MODEUSHARV-60](https://issues.folio.org/browse/MODEUSHARV-60) Update to the latest RMB 33.* release

# 3.1.2
* [MODEUSHARV-48](https://issues.folio.org/browse/MODEUSHARV-48) Error when harvesting (large) TR report, increase default container memory

# 3.1.1
* [MODEUSHARV-46](https://issues.folio.org/browse/MODEUSHARV-42) Passing long strings as message for Throwable causes application to hang

# 3.1.0
* [MODEUSHARV-35](https://issues.folio.org/browse/MODEUSHARV-35) Fetch master reports with additional attributes
* [MODEUSHARV-34](https://issues.folio.org/browse/MODEUSHARV-34) Upgrade RMB to v33
* [MODEUSHARV-38](https://issues.folio.org/browse/MODEUSHARV-38) Fix Guava Security Vulnerability CVE-2020-8908

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
