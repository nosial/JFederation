# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - Ongoing

This is an ongoing update



## [1.0.2] - 2026-08-10

### Added
 - Added `autoAssign` property to `OperatorRecord`.
 - Added `FederationClient.setAutoAssign()` for toggling an operator's auto-assign eligibility.
 - Added `FederationClient.listReportEvidenceRecords()` overloads for retrieving evidence associated with a report.
 - Added `OPERATOR_AUTO_ASSIGN_CHANGED` to `AuditLogType`.

### Changed
 - Updated `OperatorsClientTest` and `ReportsClientTest` with test units covering the new auto-assign and report-evidence functionality.
 - Updated `EnumParityTest` to include the new audit log type.




## [1.0.0] - 2026-08-09

Initial release of JFederation