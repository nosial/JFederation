package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Classification flags returned by the content scanning system to indicate the nature of scanned
 * content. Each constant maps to the uppercase string value the server expects in JSON payloads.
 */
public enum ClassificationFlag
{
    MALICIOUS("MALICIOUS"),
    SUSPICIOUS("SUSPICIOUS"),
    NORMAL("NORMAL");

    private final String value;

    ClassificationFlag(String value)
    {
        this.value = value;
    }

    /**
     * Returns the uppercase string value used in JSON serialization.
     *
     * @return The JSON-safe classification flag
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }
}
