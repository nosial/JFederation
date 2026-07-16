package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Identifies the type of a named entity extracted during content scanning.
 * Each constant maps to the lowercase string value the server expects in JSON payloads.
 */
public enum NamedEntityType
{
    DOMAIN("domain"),
    URL("url"),
    EMAIL("email"),
    IPv4("ipv4"),
    IPv6("ipv6");

    private final String value;

    NamedEntityType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the lowercase string value used in JSON serialization.
     *
     * @return The JSON-safe entity type
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }
}
