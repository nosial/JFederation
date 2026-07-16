package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines the nature of a relationship between two entities in the Federation graph.
 * Each constant maps to the lowercase string value the server expects in JSON payloads.
 */
public enum EntityRelationshipType
{
    ALTERNATIVE("alternative"),
    PROXY("proxy"),
    CHILD("child");

    private final String value;

    EntityRelationshipType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the lowercase string value used in JSON serialization.
     *
     * @return The JSON-safe relationship type
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }
}
