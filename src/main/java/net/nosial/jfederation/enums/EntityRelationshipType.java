package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines the nature of a relationship between two entities in the Federation graph.
 * Each constant maps to the uppercase string value the server exchanges in JSON payloads.
 */
public enum EntityRelationshipType
{
    ALTERNATIVE("ALTERNATIVE"),
    PROXY("PROXY"),
    CHILD("CHILD");

    private final String value;

    EntityRelationshipType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the uppercase string value used in JSON serialization.
     *
     * @return The JSON-safe relationship type
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }
}
