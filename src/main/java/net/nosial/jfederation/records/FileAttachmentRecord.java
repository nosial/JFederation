package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata about a file attachment stored in the Federation server.
 *
 * @param uuid the unique attachment identifier
 * @param evidenceUuid the UUID of the evidence record this attachment belongs to
 * @param fileName the original file name
 * @param fileSize the file size in bytes
 * @param fileMime the MIME type of the file
 * @param created the upload timestamp (Unix epoch seconds)
 */
public record FileAttachmentRecord(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("evidence") String evidenceUuid,
    @JsonProperty("file_name") String fileName,
    @JsonProperty("file_size") long fileSize,
    @JsonProperty("file_mime") String fileMime,
    @JsonProperty("created") long created
) { }
