package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.SuggestedAction;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The complete result of a content scan, including resolved entities, classification, risk score,
 * and a suggested action. Wraps raw JSON nodes and provides typed accessors for each component.
 *
 * @param resolvedEntityNodes list of raw JSON nodes for resolved entities found in the content
 * @param authorEntityNode the raw JSON node for the author entity, or {@code null}
 * @param classificationNode the raw JSON node for the content classification, or {@code null}
 * @param riskScore the computed risk score for the scanned content
 * @param suggestedAction the recommended action based on the risk score
 * @param suggestedLiftTimestamp when the suggested temporary block should be automatically lifted (Unix epoch seconds)
 * @param scanResults a map of individual scanner names to their risk scores
 */
public record ScannedContent(
    @JsonProperty("resolved_entities") List<JsonNode> resolvedEntityNodes,
    @JsonProperty("author_entity") JsonNode authorEntityNode,
    @JsonProperty("classification") JsonNode classificationNode,
    @JsonProperty("risk_score") double riskScore,
    @JsonProperty("suggested_action") SuggestedAction suggestedAction,
    @JsonProperty("suggested_lift_timestamp") Integer suggestedLiftTimestamp,
    @JsonProperty("scan_results") Map<String, Double> scanResults
) {
    /**
     * Deserialises and returns the resolved entities found in the scanned content.
     *
     * @return A list of {@link ResolvedEntity} entries, or an empty list if none
     */
    public List<ResolvedEntity> getResolvedEntities() {
        if (resolvedEntityNodes == null) {
            return Collections.emptyList();
        }
        return resolvedEntityNodes.stream()
            .map(node -> Json.mapper().convertValue(node, ResolvedEntity.class))
            .toList();
    }

    /**
     * Deserialises and returns the author entity.
     *
     * @return A {@link ResolvedEntity}, or {@code null} if not present
     */
    public ResolvedEntity getAuthorEntity() {
        if (authorEntityNode == null || authorEntityNode.isNull()) {
            return null;
        }
        return Json.mapper().convertValue(authorEntityNode, ResolvedEntity.class);
    }

    /**
     * Deserialises and returns the content classification.
     *
     * @return A {@link ContentClassification}, or {@code null} if not present
     */
    public ContentClassification getClassification() {
        if (classificationNode == null || classificationNode.isNull()) {
            return null;
        }
        return Json.mapper().convertValue(classificationNode, ContentClassification.class);
    }

    /**
     * Returns the suggested lift timestamp only when the suggested action is a temporary block.
     *
     * @return The lift timestamp, or {@code null} if the action is not a temporary block
     */
    public Integer suggestedLiftTimestamp() {
        if (suggestedAction != SuggestedAction.TEMPORARILY_BLOCK_ENTITY) {
            return null;
        }
        return suggestedLiftTimestamp;
    }
}
