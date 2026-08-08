package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.RecordType;

/**
 * A single search result returned by the Federation search endpoint. Wraps the raw JSON node for
 * the matching record and provides a typed accessor that resolves the record based on its type.
 *
 * @param type the {@link RecordType} of the matched record
 * @param recordNode the raw JSON node for the matched record
 */
public record SearchResult(
    @JsonProperty("type") RecordType type,
    @JsonProperty("record") JsonNode recordNode)
{
    @SuppressWarnings("unchecked")
    public <T> T getRecord()
    {
        return switch (type)
        {
            case ENTITY -> (T) Json.mapper().convertValue(recordNode, EntityRecord.class);
            case EVIDENCE -> (T) Json.mapper().convertValue(recordNode, EvidenceRecord.class);
            case BLACKLIST -> (T) Json.mapper().convertValue(recordNode, BlacklistRecord.class);
            case REPORT -> (T) Json.mapper().convertValue(recordNode, ReportRecord.class);
            case ATTACHMENT -> (T) Json.mapper().convertValue(recordNode, FileAttachmentRecord.class);
            case AUDIT_LOG -> (T) Json.mapper().convertValue(recordNode, AuditLog.class);
            case OPERATOR -> (T) Json.mapper().convertValue(recordNode, OperatorRecord.class);
        };
    }
}
