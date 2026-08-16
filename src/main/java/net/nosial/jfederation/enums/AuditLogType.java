package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates every type of event that can appear in the Federation audit log.
 * Each constant maps to the uppercase string value the server expects in JSON payloads.
 */
public enum AuditLogType
{
    OPERATOR_CREATED("OPERATOR_CREATED"),
    OPERATOR_DELETED("OPERATOR_DELETED"),
    OPERATOR_DISABLED("OPERATOR_DISABLED"),
    OPERATOR_ENABLED("OPERATOR_ENABLED"),
    OPERATOR_UPDATED("OPERATOR_UPDATED"),
    ATTACHMENT_UPLOADED("ATTACHMENT_UPLOADED"),
    ATTACHMENT_DELETED("ATTACHMENT_DELETED"),
    EVIDENCE_SUBMITTED("EVIDENCE_SUBMITTED"),
    EVIDENCE_UPDATED("EVIDENCE_UPDATED"),
    EVIDENCE_DELETED("EVIDENCE_DELETED"),
    REPORT_GENERATED("REPORT_GENERATED"),
    REPORT_SUBMITTED("REPORT_SUBMITTED"),
    REPORT_OPERATOR_ASSIGNED("REPORT_OPERATOR_ASSIGNED"),
    REPORT_CLOSED("REPORT_CLOSED"),
    REPORT_DELETED("REPORT_DELETED"),
    ENTITY_DELETED("ENTITY_DELETED"),
    ENTITY_BLACKLISTED("ENTITY_BLACKLISTED"),
    ENTITY_PUSHED("ENTITY_PUSHED"),
    ENTITY_UPDATED("ENTITY_UPDATED"),
    BLACKLIST_DELETED("BLACKLIST_DELETED"),
    BLACKLIST_LIFTED("BLACKLIST_LIFTED"),
    BLACKLIST_EXTENDED("BLACKLIST_EXTENDED"),
    OTHER("OTHER");

    private final String value;

    AuditLogType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the uppercase string value used in JSON serialization.
     *
     * @return The JSON-safe audit log type
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }

}
