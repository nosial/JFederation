package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.AuditLogType;

/**
 * Immutable record representing a single entry in the Federation audit log.
 *
 * @param uuid the unique audit log entry identifier
 * @param operatorUuid the UUID of the operator who performed the action, or {@code null}
 * @param entityUuid the UUID of the affected entity, or {@code null}
 * @param blacklistUuid the UUID of the affected blacklist record, or {@code null}
 * @param evidenceUuid the UUID of the affected evidence record, or {@code null}
 * @param fileAttachmentUuid the UUID of the affected file attachment, or {@code null}
 * @param type the type of audit event
 * @param message a human-readable description of the event
 * @param timestamp the event timestamp (Unix epoch seconds)
 */
public record AuditLog(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("operator") String operatorUuid,
    @JsonProperty("entity") String entityUuid,
    @JsonProperty("blacklist") String blacklistUuid,
    @JsonProperty("evidence") String evidenceUuid,
    @JsonProperty("file_attachment") String fileAttachmentUuid,
    @JsonProperty("type") AuditLogType type,
    @JsonProperty("message") String message,
    @JsonProperty("timestamp") long timestamp)
{ }
