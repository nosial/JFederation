package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.SuggestedAction;

import java.util.List;

/**
 * Result of querying an entity and its direct relationship group.
 *
 * @param entityRecord the queried entity
 * @param relatedEntities the other entities in the queried entity's relationship group
 * @param activeBlacklists active blacklist records for the queried entity and its relationship group
 * @param suggestedAction the action recommended from the active blacklist records, or {@code null}
 * @param suggestedLiftTimestamp the timestamp at which a temporary block can be lifted, or {@code null}
 */
public record EntityQueryResult(
    @JsonProperty("entity_record") EntityRecord entityRecord,
    @JsonProperty("related_entities") List<EntityRecord> relatedEntities,
    @JsonProperty("active_blacklists") List<BlacklistRecord> activeBlacklists,
    @JsonProperty("suggested_action") SuggestedAction suggestedAction,
    @JsonProperty("suggested_lift_timestamp") Long suggestedLiftTimestamp
) { }
