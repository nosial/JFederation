# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.12] - 2026-10-06

Jitpack fix



## [1.0.11] - 2026-10-06

Rate limit Fix for Jitpack, updated workflow action versions.



## [1.0.10] - 2026-10-01

This update follows version `v1.0-R4` of the OFD Specification.

### Changed
 - The entity relationship security test now expects operators with client or management permissions to set and clear
   entity relationships, and operators holding only operator permissions to be rejected with HTTP 403. The client
   itself is unchanged.


## [1.0.9] - 2026-09-30

This update migrates the library to Jackson 3 and OkHttp 5, which changes public API types.

### Changed
 - **Breaking:** Migrated from Jackson 2 (`com.fasterxml.jackson`) to Jackson 3 (`tools.jackson`, `3.2.3`).
   `Json.mapper()`, `Json.readTree()`, `FederationClient.getSpecification()` and the raw `JsonNode` components of
   `EntityRecord`, `EvidenceRecord`, `SearchResult`, `ScannedContent`, `ResolvedEntity` and `ReportSubmission` now use
   the `tools.jackson.databind` types. Jackson annotations remain in `com.fasterxml.jackson.annotation`.
 - `Json` now wraps `JacksonException` (unchecked in Jackson 3) instead of `JsonProcessingException`.
 - The shared mapper disables `FAIL_ON_NULL_FOR_PRIMITIVES`, keeping the Jackson 2 behavior of reading JSON `null`
   into primitive fields as their default value.
 - **Breaking:** Upgraded OkHttp to `5.5.0`, using the `com.squareup.okhttp3:okhttp-jvm` artifact required by Maven
   builds. Callers passing their own `OkHttpClient` to `FederationClient` must use OkHttp 5.
 - Upgraded SLF4J to `2.0.20`, Logback to `1.6.4`, JUnit to `6.1.3`, and the compiler, surefire, source and javadoc
   Maven plugins to their latest versions.
 - Updated the client integration tests for servers that auto-assign new reports to an operator.

### Fixed
 - `downloadAttachment()` no longer trusts the file name in the server's `Content-Disposition` header as a path.
   Only its final path segment is used, and the download is refused if it would resolve outside the target
   directory. Previously a name such as `../../file` could write outside the chosen directory.
 - Removed response-body null checks that could never trigger under OkHttp 5.
 - Test clients created by the test suite are now closed after each test instead of leaking their connection pools
   and dispatcher threads.
 - Fixed test assertions that could never fail and unused test values that hid missing checks.


## [1.0.8] - 2026-09-30

Following versions `v1.0-R2` and `v1.0-R3` of the OFD Specification.

### Added
 - `ServerInformation.allowIllegalContent` (`allow_illegal_content`) and `ServerInformation.isIllegalContentAllowed()`.
   A server that declines illegal content rejects `ILLEGAL_CONTENT` reports with HTTP 403; servers that don't publish
   the member are treated as accepting them.

### Changed
 - `setEntityRelationship()` names its second parameter `relatedEntityIdentifier`, as it accepts any entity identifier
   form, not only a UUID.

### Removed
 - `OperatorRecord.accessToken`, which is not part of an operator record and is no longer returned by the server.
   The access token of a new operator is still available from `OperatorCreated.accessToken()`.


## [1.0.7] - 2026-08-24

This update introduces changes from the specification

### Changed
 - Blacklist records now reference the supporting `report` instead of `evidence`. `blacklistEntity()`
   takes a `reportUuid` and sends `report_uuid`, and `BlacklistRecord` exposes `reportUuid()` mapped
   from the `report` field.
 - Updated client integration tests to blacklist entities using a submitted report.


## [1.0.6] - 2026-08-17

This update introduces changes from the specification

### Added
 - Added `ServerInformation` public-access capability metadata for entity metadata, content scanning, entity queries,
   global search availability, and enabled/public dedicated search record types.



## [1.0.5] - 2026-08-16

This update introduces changes from the specification

### Added
 - Added `EntityQueryResult` and `FederationClient.queryEntity()` for `GET /entities/{identifier}/query`.
 - Added default-parameter overloads for `scanContent(List<ContentInput>)`.

### Changed
 - Replaced the legacy operator-mutation audit types with `OPERATOR_UPDATED`.
 - Removed audit types no longer emitted by FederationLib: `ENTITY_REPUTATION_CLEARED`,
   `ENTITY_WHITELIST_CHANGED`, and `BLACKLIST_ATTACHMENT_ADDED`.
 - Updated client integration and enum-parity tests for the current entity-query and audit-log contracts.



## [1.0.4] - 2026-08-13

This update introduces changes from the specification

### Added
 - Added `FederationClient.classifyEvidence()` for `PATCH /evidence/{uuid}/classify`.
 - Added a `submitEvidence()` overload accepting an optional immutable `ClassificationFlag`.
 - Added Java client tests for immutable evidence classification, classified submission, and management-only
   classification authorization.



## [1.0.3] - 2026-08-12

This update introduces changes from the specification

### Added
 - Added `ContentInput` record for representing evidence content (`text_content`, `note`, `tag`, `confidential`, `metadata`)
 - Added `FederationClient.scanContent()` overloads accepting a `ContentInput` or a list of `ContentInput` entries.
 - Added `FederationClient.submitReport()` overloads accepting a `ContentInput` or a list of `ContentInput` entries.

### Changed
 - `FederationClient.scanContent()` now sends an `evidence` payload (single object or array) instead of a top-level
   `content` string; the top-level `metadata` parameter was replaced by the per-evidence `ContentInput.metadata` field.
 - `FederationClient.submitReport()` now sends an `evidence` payload (single object or array) instead of a `content`
    string, and the `evidence_tag` parameter was removed (tags are now carried by each `ContentInput`).
 - `FederationClient.setEntityRelationship()` now sends `target_identifier` instead of `target_entity_uuid`, matching
    the FederationLib API.
 - `ReportSubmission.getEvidence()` now returns a `List<EvidenceRecord>`.
 - Updated `FederationClientTestBase` and related test units to align with the evidence-based `scanContent` / `submitReport` API.

### Removed
 - Removed the attachment property from `ReportSubmission` and the `submitReport()` overload that uploaded local file paths
   / remote URLs as attachments. Attachments are now uploaded separately after submission via
   `FederationClient.uploadFileAttachment()` and `FederationClient.uploadFileAttachmentFromUrl()`.



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