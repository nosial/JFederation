package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.AuditLogType;
import net.nosial.jfederation.enums.ContentType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.RecordType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the Federation server's identity, feature flags, and record counts. Returned
 * by the {@code /info} endpoint and useful for monitoring server health and capacity.
 *
 * @param serverName the server instance name
 * @param apiVersion the API version string
 * @param publicAuditLogs whether audit logs are publicly visible
 * @param publicEvidence whether evidence records are publicly visible
 * @param publicBlacklist whether blacklist records are publicly visible
 * @param publicEntities whether entities are publicly visible
 * @param publicReports whether reports are publicly visible
 * @param publicAuditLogsVisibility audit log types visible to unauthenticated clients
 * @param publicEntityMetadata whether entity metadata is included for unauthenticated clients
 * @param publicTelephoneEntities whether telephone entities are exposed to unauthenticated requests like other
 *                                entities, rather than hidden
 * @param publicScanContent whether content scanning is available without authentication
 * @param collectsPublicScanContent whether the server may store content submitted for scanning by unauthenticated
 *                                  requests, or records derived from it, or {@code null} when the server does not
 *                                  publish it
 * @param publicQueryEntity whether entity relationship queries are available without authentication
 * @param searchEnabled whether search functionality is enabled
 * @param publicSearch whether the global search endpoint is available without authentication
 * @param searchTypes record types with enabled dedicated search endpoints
 * @param publicSearchTypes record types with dedicated search endpoints available without authentication
 * @param allowIllegalContent whether the server accepts reports with the {@code ILLEGAL_CONTENT} incident type, or
 *                            {@code null} when the server does not publish it; see {@link #isIllegalContentAllowed()}
 * @param allowUrlUploads whether the server accepts URL uploads, or {@code null} when the server does not publish
 *                        it; see {@link #isUrlUploadsAllowed()}
 * @param scanContentTypes the content types content scanning interprets according to their type
 * @param maxScanFiles the maximum number of files a scan request may carry, 0 when the server ignores the files of
 *                     scan requests or does not publish it
 * @param maxScanFileSize the maximum size in bytes of each file a scan request may carry, 0 when the server ignores
 *                        the files of scan requests or does not publish it
 * @param concerns the concerns the server can assess
 * @param defaultConcerns the concerns the server assesses when a request names none
 * @param auditLogRecords total audit log record count
 * @param blacklistRecords total blacklist record count
 * @param knownEntities total known entity count
 * @param evidenceRecords total evidence record count
 * @param fileAttachmentRecords total file attachment record count
 * @param operators total operator count
 * @param reports total report count
 */
public record ServerInformation(
    @JsonProperty("name") String serverName,
    @JsonProperty("api_version") String apiVersion,
    @JsonProperty("public_audit_logs") boolean publicAuditLogs,
    @JsonProperty("public_evidence") boolean publicEvidence,
    @JsonProperty("public_blacklist") boolean publicBlacklist,
    @JsonProperty("public_entities") boolean publicEntities,
    @JsonProperty("public_reports") boolean publicReports,
    @JsonProperty("public_entity_metadata") boolean publicEntityMetadata,
    @JsonProperty("public_telephone_entities") boolean publicTelephoneEntities,
    @JsonProperty("public_scan_content") boolean publicScanContent,
    @JsonProperty("collects_public_scan_content") Boolean collectsPublicScanContent,
    @JsonProperty("public_query_entity") boolean publicQueryEntity,
    @JsonProperty("search_enabled") boolean searchEnabled,
    @JsonProperty("public_search") boolean publicSearch,
    @JsonProperty("search_types") List<RecordType> searchTypes,
    @JsonProperty("public_search_types") List<RecordType> publicSearchTypes,
    @JsonProperty("allow_illegal_content") Boolean allowIllegalContent,
    @JsonProperty("allow_url_uploads") Boolean allowUrlUploads,
    @JsonProperty("scan_content_types") List<ContentType> scanContentTypes,
    @JsonProperty("max_scan_files") int maxScanFiles,
    @JsonProperty("max_scan_file_size") long maxScanFileSize,
    @JsonProperty("concerns") List<IncidentType> concerns,
    @JsonProperty("default_concerns") List<IncidentType> defaultConcerns,
    @JsonProperty("public_audit_logs_visibility") List<AuditLogType> publicAuditLogsVisibility,
    @JsonProperty("audit_log_records") int auditLogRecords,
    @JsonProperty("blacklist_records") int blacklistRecords,
    @JsonProperty("known_entities") int knownEntities,
    @JsonProperty("evidence_records") int evidenceRecords,
    @JsonProperty("file_attachment_records") int fileAttachmentRecords,
    @JsonProperty("operators") int operators,
    @JsonProperty("reports") int reports
) {
    /**
     * Compact constructor, values this client does not recognize are left out of the lists, and absent lists are
     * empty, except for the content types of scanning which always include {@code TEXT}.
     */
    public ServerInformation
    {
        searchTypes = known(searchTypes);
        publicSearchTypes = known(publicSearchTypes);
        publicAuditLogsVisibility = known(publicAuditLogsVisibility);
        concerns = known(concerns);
        defaultConcerns = known(defaultConcerns);
        scanContentTypes = known(scanContentTypes);
        if (!scanContentTypes.contains(ContentType.TEXT))
        {
            List<ContentType> types = new ArrayList<>(scanContentTypes);
            types.addFirst(ContentType.TEXT);
            scanContentTypes = List.copyOf(types);
        }
    }

    private static <T> List<T> known(List<T> values)
    {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    /**
     * Returns whether the server accepts URL uploads through
     * {@link net.nosial.jfederation.FederationClient#uploadFileAttachmentByUrl(String, String)}. A client should not
     * submit a URL upload to a server that does not publish {@code allow_url_uploads} as true, and should upload the
     * file itself with {@link net.nosial.jfederation.FederationClient#uploadFileAttachmentFromUrl(String, String)}
     * instead.
     *
     * @return true only if the server publishes {@code allow_url_uploads} as true
     */
    public boolean isUrlUploadsAllowed()
    {
        return Boolean.TRUE.equals(allowUrlUploads);
    }

    /**
     * Returns whether the content scanning of the server interprets the given content type according to its type.
     *
     * @param contentType The content type
     * @return true if the content type is among {@link #scanContentTypes()}
     */
    public boolean isScanContentTypeSupported(ContentType contentType)
    {
        return scanContentTypes.contains(contentType);
    }

    /**
     * Returns the major version of the interface the server implements, as defined by the OFD versioning section.
     *
     * @return The major version, or -1 when the {@code api_version} cannot be parsed
     */
    public int getApiMajorVersion()
    {
        if (apiVersion == null)
        {
            return -1;
        }

        try
        {
            return Integer.parseInt(apiVersion.split("\\.", 2)[0].trim());
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    /**
     * Returns the minor version of the interface the server implements, as defined by the OFD versioning section.
     *
     * @return The minor version, or -1 when the {@code api_version} cannot be parsed
     */
    public int getApiMinorVersion()
    {
        if (apiVersion == null)
        {
            return -1;
        }

        String[] parts = apiVersion.split("\\.", 3);
        if (parts.length < 2)
        {
            return -1;
        }

        try
        {
            return Integer.parseInt(parts[1].trim());
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }

    /**
     * Returns whether the server implements the given interface version or a later minor version of the same major
     * version, so that a client can use what that version introduced, such as the file URLs of scanned content (2.2).
     *
     * @param major The major version
     * @param minor The minor version
     * @return true if the server reports the same major version and at least the given minor version
     */
    public boolean isApiVersionAtLeast(int major, int minor)
    {
        return getApiMajorVersion() == major && getApiMinorVersion() >= minor;
    }

    /**
     * Returns whether the server accepts reports with the {@code ILLEGAL_CONTENT} incident type. A server that
     * declines them rejects such reports with HTTP 403. Servers that predate the {@code allow_illegal_content}
     * member do not publish it and accept every incident type.
     *
     * @return true unless the server publishes {@code allow_illegal_content} as false
     */
    public boolean isIllegalContentAllowed()
    {
        return !Boolean.FALSE.equals(allowIllegalContent);
    }
}
