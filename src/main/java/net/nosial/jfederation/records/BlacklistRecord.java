package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.IncidentType;

/**
 * Immutable record representing a blacklist entry in the Federation server.
 *
 * @param uuid the unique blacklist record identifier
 * @param operatorUuid the UUID of the operator who created the blacklist entry
 * @param entityUuid the UUID of the blacklisted entity
 * @param evidenceUuid the UUID of the evidence supporting the blacklist
 * @param type the incident type that triggered the blacklist
 * @param lifted whether the blacklist has been lifted
 * @param liftedBy the UUID of the operator who lifted the blacklist, or {@code null}
 * @param expires the expiration timestamp (Unix epoch seconds), or {@code null}
 * @param created the creation timestamp (Unix epoch seconds)
 */
public record BlacklistRecord(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("operator") String operatorUuid,
    @JsonProperty("entity") String entityUuid,
    @JsonProperty("evidence") String evidenceUuid,
    @JsonProperty("type") IncidentType type,
    @JsonProperty("lifted") boolean lifted,
    @JsonProperty("lifted_by") String liftedBy,
    @JsonProperty("expires") Long expires,
    @JsonProperty("created") long created)
{ }
