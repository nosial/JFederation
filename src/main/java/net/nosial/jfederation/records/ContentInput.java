package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * A single piece of content submitted for scanning or attached to a report submission,
 * mirroring the FederationLib {@code ContentInput} object.
 *
 * @param textContent The textual content of the evidence, or {@code null}
 * @param note An optional note by the operator, or {@code null}
 * @param tag An optional tag name for the evidence record, or {@code null}
 * @param confidential Whether the evidence should be marked confidential
 * @param metadata Optional arbitrary metadata to attach to the evidence record, or {@code null}
 */
public record ContentInput(
    @JsonProperty("text_content") String textContent,
    @JsonProperty("note") String note,
    @JsonProperty("tag") String tag,
    @JsonProperty("confidential") boolean confidential,
    @JsonProperty("metadata") Map<String, Object> metadata
)
{
    /**
     * Creates a content input with only text content.
     *
     * @param textContent The textual content of the evidence
     */
    public ContentInput(String textContent)
    {
        this(textContent, null, null, false, null);
    }

    /**
     * Creates a content input with text content, an optional note, and an optional tag.
     *
     * @param textContent The textual content of the evidence
     * @param note An optional note by the operator
     * @param tag An optional tag name for the evidence record
     */
    public ContentInput(String textContent, String note, String tag)
    {
        this(textContent, note, tag, false, null);
    }
}
