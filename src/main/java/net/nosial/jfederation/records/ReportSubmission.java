package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;

import java.util.Collections;
import java.util.List;

/**
 * Aggregated result returned when submitting a report. Wraps the raw JSON nodes for the created
 * report, evidence, and any uploaded attachments, and provides typed accessors for each.
 *
 * @param reportNode the raw JSON node for the created report record
 * @param evidenceNode the raw JSON node for the created evidence record
 * @param attachmentNodes optional list of raw JSON nodes for attachment upload results
 */
public record ReportSubmission(
    @JsonProperty("report") JsonNode reportNode,
    @JsonProperty("evidence") JsonNode evidenceNode,
    @JsonProperty("attachments") List<JsonNode> attachmentNodes
)
{
    /**
     * Deserialises and returns the created report record.
     *
     * @return A {@link ReportRecord} populated from the server response
     */
    public ReportRecord getReport()
    {
        return Json.mapper().convertValue(reportNode, ReportRecord.class);
    }

    /**
     * Deserialises and returns the created evidence record.
     *
     * @return A {@link EvidenceRecord} populated from the server response
     */
    public EvidenceRecord getEvidence()
    {
        return Json.mapper().convertValue(evidenceNode, EvidenceRecord.class);
    }
}
