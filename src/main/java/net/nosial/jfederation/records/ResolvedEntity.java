package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;

import java.util.Collections;
import java.util.List;

/**
 * A fully resolved entity returned by the content scanning system, including its position in the
 * scanned text, any active blacklists, and optional parent entity relationship. Wraps raw JSON
 * nodes and provides typed accessors for each component.
 *
 * @param entityNode the raw JSON node for the resolved entity record
 * @param entityPositionNode the raw JSON node for the entity's position in scanned text, or {@code null}
 * @param activeBlacklistNodes list of raw JSON nodes for active blacklist records on this entity
 * @param parentEntityNode the raw JSON node for a parent resolved entity, or {@code null}
 */
public record ResolvedEntity(
    @JsonProperty("entity") JsonNode entityNode,
    @JsonProperty("entity_position") JsonNode entityPositionNode,
    @JsonProperty("active_blacklists") List<JsonNode> activeBlacklistNodes,
    @JsonProperty("parent_entity") JsonNode parentEntityNode)
{
    /**
     * Deserialises and returns the resolved entity record.
     *
     * @return An {@link EntityRecord} populated from the server response
     */
    public EntityRecord getEntity() {
        return Json.mapper().convertValue(entityNode, EntityRecord.class);
    }

    /**
     * Deserialises and returns the entity's position in the scanned text.
     *
     * @return A {@link ResolvedEntityPosition}, or {@code null} if not present
     */
    public ResolvedEntityPosition getEntityPosition() {
        if (entityPositionNode == null || entityPositionNode.isNull()) {
            return null;
        }
        return Json.mapper().convertValue(entityPositionNode, ResolvedEntityPosition.class);
    }

    /**
     * Deserialises and returns the active blacklist records on this entity.
     *
     * @return A list of {@link BlacklistRecord} entries, or an empty list if none
     */
    public List<BlacklistRecord> getActiveBlacklists() {
        if (activeBlacklistNodes == null) {
            return Collections.emptyList();
        }
        return activeBlacklistNodes.stream()
            .map(node -> Json.mapper().convertValue(node, BlacklistRecord.class))
            .toList();
    }

    /**
     * Deserialises and returns the parent resolved entity, if one exists.
     *
     * @return A {@link ResolvedEntity}, or {@code null} if there is no parent
     */
    public ResolvedEntity getParentEntity() {
        if (parentEntityNode == null || parentEntityNode.isNull()) {
            return null;
        }
        return Json.mapper().convertValue(parentEntityNode, ResolvedEntity.class);
    }
}
