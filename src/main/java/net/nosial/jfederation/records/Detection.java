package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.IncidentType;

/**
 * One finding of a host about scanned content with respect to one of the concerns of the scan (OFD 2.0).
 *
 * @param concern the concern the detection relates to
 * @param contentIndex the zero-based position of the {@link ContentInput} the detection relates to, 0 when a single
 *                     content input was scanned
 * @param entity the UUID of the entity the detection relates to, or {@code null}
 * @param source a host-defined name of the mechanism that made the detection, clients must not rely on its values
 * @param confidence a host-defined confidence of the detection, or {@code null}
 * @param verified false when the detection relies on context or other client assertions the host has not verified,
 *                 such a detection should be weighed as no stronger than the context supplied
 */
public record Detection(
    @JsonProperty("concern") IncidentType concern,
    @JsonProperty("content_index") int contentIndex,
    @JsonProperty("entity") String entity,
    @JsonProperty("source") String source,
    @JsonProperty("confidence") Double confidence,
    @JsonProperty("verified") boolean verified
) { }
