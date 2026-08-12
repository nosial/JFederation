# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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