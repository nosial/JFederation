package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Categories of security incidents that can be attached to blacklist records and reports, and the concerns a
 * content scan or entity query assesses. Each constant maps to the uppercase string value the server expects in JSON
 * payloads. An incident type this client does not recognize is read as {@link #OTHER}, as OFD 2.0 requires.
 */
public enum IncidentType
{
    SPAM("SPAM"),
    SCAM("SCAM"),
    SERVICE_ABUSE("SERVICE_ABUSE"),
    ILLEGAL_CONTENT("ILLEGAL_CONTENT"),
    MALWARE("MALWARE"),
    PHISHING("PHISHING"),
    ADVERTISING("ADVERTISING"),
    TRACKING("TRACKING"),
    @JsonEnumDefaultValue
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
