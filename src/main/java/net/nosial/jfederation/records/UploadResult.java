package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of uploading a file attachment, containing the server-assigned UUID and the download URL.
 *
 * @param uuid the unique identifier for the uploaded attachment
 * @param url the URL from which the attachment can be downloaded
 */
public record UploadResult(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("url") String url
) {
}
