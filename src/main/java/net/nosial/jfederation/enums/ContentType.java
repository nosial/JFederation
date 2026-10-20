package net.nosial.jfederation.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The type of the content carried by a {@link net.nosial.jfederation.records.ContentInput} or stored with an
 * evidence record (OFD 2.0). The type determines the form of the text content and the keys its context may contain;
 * a host publishes the types its content scanning interprets in
 * {@link net.nosial.jfederation.records.ServerInformation#scanContentTypes()}.
 */
public enum ContentType
{
    /**
     * Free-form text, such as a chat message. This is the default.
     */
    TEXT("TEXT"),

    /**
     * An email message, the text content is the body of the message. Context keys: {@code subject},
     * {@code sender} and {@code reply_to}.
     */
    EMAIL("EMAIL"),

    /**
     * A text message received over a telephone network, the text content is the body of the message. Context keys:
     * {@code region} (an ISO 3166-1 alpha-2 code) and {@code sender_id}.
     */
    SMS("SMS"),

    /**
     * A web page, the text content is text extracted from the page. Context keys: {@code url} (required),
     * {@code final_url}, {@code title}, {@code description} and {@code language}.
     */
    WEB_PAGE("WEB_PAGE"),

    /**
     * A DNS name observed by the client, the text content is the name. Context keys: {@code query_type}.
     */
    HOSTNAME("HOSTNAME"),

    /**
     * A URL observed by the client, the text content is an absolute URL. No context keys are defined.
     */
    URL("URL");

    private final String value;

    ContentType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the uppercase string value used in JSON serialization.
     *
     * @return The JSON-safe content type
     */
    @JsonValue
    public String getValue()
    {
        return value;
    }
}
