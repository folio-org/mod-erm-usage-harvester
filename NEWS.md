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
