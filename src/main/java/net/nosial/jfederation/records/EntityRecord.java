package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.EntityRelationshipType;

import java.util.Map;

/**
 * Immutable record representing a security entity tracked by the Federation server.
 *
 * @param uuid the unique entity identifier
 * @param host the entity hostname or IP address
 * @param id an optional user-level identifier
 * @param metadata optional metadata as a raw JSON node
 * @param whitelisted whether the entity is whitelisted
 * @param reputation the entity reputation score
 * @param reputationLastUpdated the timestamp of the last reputation update (Unix epoch seconds)
 * @param relationshipEntity the UUID of a related entity, or {@code null}
 * @param relationshipType the type of relationship to the related entity, or {@code null}
 * @param created the creation timestamp (Unix epoch seconds)
 * @param updated the last-updated timestamp (Unix epoch seconds)
 */
public record EntityRecord(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("host") String host,
    @JsonProperty("id") String id,
    @JsonProperty("metadata") JsonNode metadata,
    @JsonProperty("whitelisted") boolean whitelisted,
    @JsonProperty("reputation") double reputation,
    @JsonProperty("reputation_last_updated") long reputationLastUpdated,
    @JsonProperty("relationship_entity") String relationshipEntity,
    @JsonProperty("relationship_type") EntityRelationshipType relationshipType,
    @JsonProperty("created") long created,
    @JsonProperty("updated") long updated)
{
    /**
     * Deserializes and returns the metadata as a map, or an empty map if no metadata is present.
     * The Federation server may serialize metadata either as a JSON object or as a JSON-encoded
     * string, so both forms are handled here.
     *
     * @return A {@link Map} of key-value pairs
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadata()
    {
        if (metadata == null || metadata.isNull())
        {
            return Map.of();
        }

        JsonNode metadataNode = metadata;
        if (metadata.isTextual())
        {
            try
            {
                metadataNode = Json.mapper().readTree(metadata.asText());
            }
            catch (Exception e)
            {
                return Map.of();
            }
        }
        return Json.mapper().convertValue(metadataNode, Map.class);
    }
}
