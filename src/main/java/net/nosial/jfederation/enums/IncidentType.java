package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Categories of security incidents that can be attached to blacklist records and reports.
 * Each constant maps to the uppercase string value the server expects in JSON payloads.
 */
public enum IncidentType
{
    SPAM("SPAM"),
    SCAM("SCAM"),
    SERVICE_ABUSE("SERVICE_ABUSE"),
    ILLEGAL_CONTENT("ILLEGAL_CONTENT"),
    MALWARE("MALWARE"),
    PHISHING("PHISHING"),
    OTHER("OTHER");

    private final String value;

    IncidentType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the uppercase string value used in JSON serialization.
     *
     * @return The JSON-safe incident type
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }
}
