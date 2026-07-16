package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the types of records that can be stored, searched, and retrieved from a Federation
 * server. Each constant maps to the uppercase string value the server expects in JSON payloads.
 */
public enum RecordType
{
    ENTITY("ENTITY"),
    EVIDENCE("EVIDENCE"),
    BLACKLIST("BLACKLIST"),
    REPORT("REPORT"),
    ATTACHMENT("ATTACHMENT"),
    AUDIT_LOG("AUDIT_LOG"),
    OPERATOR("OPERATOR");

    private final String value;

    RecordType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the uppercase string value used in JSON serialization.
     *
     * @return The JSON-safe constant name
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }
}
