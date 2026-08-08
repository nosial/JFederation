package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.AuditLogType;

import java.util.List;

/**
 * Immutable snapshot of the Federation server's identity, feature flags, and record counts. Returned
 * by the {@code /server/info} endpoint and useful for monitoring server health and capacity.
 *
 * @param serverName the server instance name
 * @param apiVersion the API version string
 * @param publicAuditLogs whether audit logs are publicly visible
 * @param publicEvidence whether evidence records are publicly visible
 * @param publicBlacklist whether blacklist records are publicly visible
 * @param publicEntities whether entities are publicly visible
 * @param publicReports whether reports are publicly visible
 * @param publicAuditLogsVisibility audit log types visible to unauthenticated clients
 * @param auditLogRecords total audit log record count
 * @param blacklistRecords total blacklist record count
 * @param knownEntities total known entity count
 * @param evidenceRecords total evidence record count
 * @param fileAttachmentRecords total file attachment record count
 * @param operators total operator count
 * @param reports total report count
 */
public record ServerInformation(
    @JsonProperty("name") String serverName,
    @JsonProperty("api_version") String apiVersion,
    @JsonProperty("public_audit_logs") boolean publicAuditLogs,
    @JsonProperty("public_evidence") boolean publicEvidence,
    @JsonProperty("public_blacklist") boolean publicBlacklist,
    @JsonProperty("public_entities") boolean publicEntities,
    @JsonProperty("public_reports") boolean publicReports,
    @JsonProperty("public_audit_logs_visibility") List<AuditLogType> publicAuditLogsVisibility,
    @JsonProperty("audit_log_records") int auditLogRecords,
    @JsonProperty("blacklist_records") int blacklistRecords,
    @JsonProperty("known_entities") int knownEntities,
    @JsonProperty("evidence_records") int evidenceRecords,
    @JsonProperty("file_attachment_records") int fileAttachmentRecords,
    @JsonProperty("operators") int operators,
    @JsonProperty("reports") int reports
) {
}
