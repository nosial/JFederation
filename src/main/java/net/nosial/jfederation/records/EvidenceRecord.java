package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.enums.ClassificationFlag;

import java.util.Map;

/**
 * Immutable record representing a piece of evidence stored in the Federation server.
 *
 * @param uuid the unique evidence identifier
 * @param entityUuid the UUID of the entity this evidence is associated with
 * @param operatorUuid the UUID of the operator who submitted the evidence
 * @param confidential whether the evidence is marked as confidential
 * @param textContent the text content of the evidence
 * @param note an optional note attached to the evidence
 * @param tag an optional tag categorising the evidence
 * @param report the UUID of the report this evidence is linked to, or {@code null}
 * @param metadata optional metadata as a raw JSON node
 * @param classificationFlag the content classification flag, or {@code null}
 * @param created the creation timestamp (Unix epoch seconds)
 * @param updated the last-updated timestamp (Unix epoch seconds)
 */
public record EvidenceRecord(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("entity") String entityUuid,
    @JsonProperty("operator") String operatorUuid,
    @JsonProperty("confidential") boolean confidential,
    @JsonProperty("text_content") String textContent,
    @JsonProperty("note") String note,
    @JsonProperty("tag") String tag,
    @JsonProperty("report") String report,
    @JsonProperty("metadata") JsonNode metadata,
    @JsonProperty("classification_flag") ClassificationFlag classificationFlag,
    @JsonProperty("created") long created,
    @JsonProperty("updated") long updated)
{ }
