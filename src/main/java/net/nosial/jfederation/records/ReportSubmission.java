package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;

import java.util.Collections;
import java.util.List;

/**
 * Aggregated result returned when submitting a report. Wraps the raw JSON nodes for the created
 * report and evidence, and provides typed accessors for each. File attachments are uploaded
 * separately after submission using {@code uploadFileAttachment} / {@code uploadFileAttachmentFromUrl}.
 *
 * @param reportNode the raw JSON node for the created report record
 * @param evidenceNode the raw JSON node for the created evidence records (an array)
 */
public record ReportSubmission(
    @JsonProperty("report") JsonNode reportNode,
    @JsonProperty("evidence") JsonNode evidenceNode
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
     * Deserialises and returns the evidence records created with the report submission.
     *
     * @return A list of {@link EvidenceRecord} entries populated from the server response
     */
    public List<EvidenceRecord> getEvidence()
    {
        if (evidenceNode == null || evidenceNode.isNull())
        {
            return Collections.emptyList();
        }

        return Json.mapper().convertValue(evidenceNode, new TypeReference<>() {});
    }
}
