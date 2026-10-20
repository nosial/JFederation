package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.ContentType;

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
 * @param contentType The type of the content, or {@code null} for {@link ContentType#TEXT}, which is not sent
 * @param context Optional flat context of the content as the client observed it, whose keys are defined per content
 *                type (see {@link ContentType}), or {@code null}. Context is an assertion of the client
 */
public record ContentInput(
    @JsonProperty("text_content") String textContent,
    @JsonProperty("note") String note,
    @JsonProperty("tag") String tag,
    @JsonProperty("confidential") boolean confidential,
    @JsonProperty("metadata") Map<String, Object> metadata,
    @JsonProperty("content_type") ContentType contentType,
    @JsonProperty("context") Map<String, String> context
)
{
    /**
     * Compact constructor, the default content type is not sent to the server.
     */
    public ContentInput
    {
        if (contentType == ContentType.TEXT)
        {
            contentType = null;
        }
    }

    /**
     * Creates a content input with only text content.
     *
     * @param textContent The textual content of the evidence
     */
    public ContentInput(String textContent)
    {
        this(textContent, null, null, false, null, null, null);
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
        this(textContent, note, tag, false, null, null, null);
    }

    /**
     * Creates a content input with text content, note, tag, confidentiality and metadata, of the {@code TEXT} type.
     *
     * @param textContent The textual content of the evidence, or {@code null}
     * @param note An optional note by the operator, or {@code null}
     * @param tag An optional tag name for the evidence record, or {@code null}
     * @param confidential Whether the evidence should be marked confidential
     * @param metadata Optional arbitrary metadata to attach to the evidence record, or {@code null}
     */
    public ContentInput(String textContent, String note, String tag, boolean confidential, Map<String, Object> metadata)
    {
        this(textContent, note, tag, confidential, metadata, null, null);
    }

    /**
     * Creates a content input of the given content type.
     *
     * @param textContent The content, in the form its content type defines (eg; an absolute URL for {@code URL})
     * @param contentType The type of the content
     */
    public ContentInput(String textContent, ContentType contentType)
    {
        this(textContent, null, null, false, null, contentType, null);
    }

    /**
     * Creates a content input of the given content type with context.
     *
     * @param textContent The content, in the form its content type defines
     * @param contentType The type of the content
     * @param context The context of the content, whose keys are defined per content type, or {@code null}
     */
    public ContentInput(String textContent, ContentType contentType, Map<String, String> context)
    {
        this(textContent, null, null, false, null, contentType, context);
    }

    /**
     * Returns the type of the content.
     *
     * @return The content type, {@link ContentType#TEXT} when none was given
     */
    public ContentType effectiveContentType()
    {
        return contentType != null ? contentType : ContentType.TEXT;
    }
}
