package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.IncidentType;

/**
 * Immutable record representing a security report submitted to the Federation server.
 *
 * @param uuid the unique report identifier
 * @param submittingOperator the UUID of the operator who submitted the report
 * @param reportingEntity the UUID of the entity being reported
 * @param assignedOperator the UUID of the operator assigned to handle the report, or {@code null}
 * @param automated whether the report was generated automatically
 * @param incidentType the type of security incident
 * @param opened whether the report is still open
 * @param message the report description or message
 * @param created the creation timestamp (Unix epoch seconds)
 * @param updated the last-updated timestamp (Unix epoch seconds)
 */
public record ReportRecord(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("submitting_operator") String submittingOperator,
    @JsonProperty("reporting_entity") String reportingEntity,
    @JsonProperty("assigned_operator") String assignedOperator,
    @JsonProperty("automated") boolean automated,
    @JsonProperty("incident_type") IncidentType incidentType,
    @JsonProperty("opened") boolean opened,
    @JsonProperty("message") String message,
    @JsonProperty("created") long created,
    @JsonProperty("updated") long updated
) { }
