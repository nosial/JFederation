package net.nosial.jfederation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.ClassificationFlag;
import net.nosial.jfederation.enums.EntityRelationshipType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.RecordType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe API client for interacting with a Federation server. Every public method corresponds
 * to a REST endpoint and handles serialisation, authentication, and error mapping.
 *
 * <p>Instances are created with a server endpoint and optional access token, and should be closed
 * via {@link #close()} when no longer needed to release the underlying HTTP connection pool.
 */
public final class FederationClient implements AutoCloseable
{
    private static final Logger log = LoggerFactory.getLogger(FederationClient.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int DEFAULT_MAX_FILE_SIZE = 52_428_800; // 50 MB, matching the FederationLib client default

    private final String endpoint;
    private final OkHttpClient httpClient;
    private volatile String accessToken;

    /**
     * Creates a client without authentication.
     *
     * @param endpoint The base URL of the Federation server
     */
    public FederationClient(String endpoint)
    {
        this(endpoint, null);
    }

    /**
     * Creates a client with the given access token and default HTTP settings.
     *
     * @param endpoint The base URL of the Federation server
     * @param accessToken The access token for API authentication, or {@code null} for anonymous access
     */
    public FederationClient(String endpoint, String accessToken)
    {
        this(endpoint, accessToken, new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(5, 30, TimeUnit.SECONDS))
            .build());
    }

    /**
     * FederationClient Constructor
     *
     * @param endpoint The base URL of the Federation server
     * @param accessToken The access token for API authentication, or {@code null} for anonymous access
     * @param httpClient The OkHttpClient instance to use for all requests
     * @throws IllegalArgumentException if the endpoint is {@code null}, not a valid URL, or lacks a host
     */
    public FederationClient(String endpoint, String accessToken, OkHttpClient httpClient)
    {
        Objects.requireNonNull(endpoint, "endpoint must not be null");

        URL parsedUrl;

        try
        {
            parsedUrl = new URI(endpoint).toURL();
        }
        catch (URISyntaxException | MalformedURLException | IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Endpoint must be a valid URL: " + endpoint, e);
        }

        if (parsedUrl.getHost() == null || parsedUrl.getHost().isEmpty())
        {
            throw new IllegalArgumentException("Endpoint must have a valid host");
        }

        this.endpoint = endpoint.replaceAll("/+$", "");
        this.httpClient = httpClient;
        setAccessToken(accessToken);
        log.info("FederationClient created for endpoint: {}", this.endpoint);
    }

    /**
     * Returns the Federation server endpoint URL.
     *
     * @return The endpoint URL
     */
    public String getEndpoint()
    {
        return this.endpoint;
    }

    /**
     * Returns the current access token.
     *
     * @return The access token, or {@code null} if unauthenticated
     */
    public String getAccessToken()
    {
        return accessToken;
    }

    /**
     * Sets or clears the access token used for API authentication.
     *
     * @param accessToken The new access token, or {@code null} to clear it
     * @throws IllegalArgumentException if the token is an empty string or contains whitespace
     */
    public void setAccessToken(String accessToken)
    {
        if (accessToken != null)
        {
            if (accessToken.isEmpty())
            {
                throw new IllegalArgumentException("Token cannot be an empty string");
            }

            if (accessToken.chars().anyMatch(Character::isWhitespace))
            {
                throw new IllegalArgumentException("Token cannot contain whitespace");
            }
        }

        this.accessToken = accessToken;
        log.debug("Client access token updated");
    }

    /**
     * Releases the underlying HTTP connection pool and shuts down the dispatcher's executor service.
     */
    @Override
    public void close()
    {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        log.info("FederationClient closed for endpoint: {}", endpoint);
    }

    /**
     * Builds the URL path
     * @param path The path
     * @return The full HttpUrl object
     */
    private HttpUrl buildUrl(String path)
    {
        path = path.replaceAll("^/+", "");
        return HttpUrl.parse(endpoint + "/" + path);
    }

    /**
     * Builds a new HTTP request builder with the specified path.
     *
     * @param path The URL path for the request.
     * @return A Request.Builder object configured with the base URL and default headers.
     */
    private Request.Builder buildRequest(String path)
    {
        HttpUrl url = buildUrl(path);
        Request.Builder builder = new Request.Builder().url(url).header("Accept", "application/json");

        String token = this.accessToken;
        if (token != null)
        {
            builder.header("Authorization", "Bearer " + token);
        }

        return builder;
    }

    /**
     * Sends an HTTP request with the specified parameters.
     *
     * @param method HTTP method to use (e.g., "GET", "POST").
     * @param path URL path to send the request to.
     * @param data Map containing data to be included in the request body.
     * @param expectedStatus Array of expected HTTP status codes. The method will return successfully if any of these
     *                       status codes is matched.
     * @param errorMessage Error message to include in exceptions if the status code does not match one of the expected
     *                     codes or if an I/O error occurs.
     * @return JSON response node received from the server.
     */
    private JsonNode makeRequest(String method, String path, Map<String, Object> data,
                                  int expectedStatus, String errorMessage)
    {
        return makeRequest(method, path, data, new int[]{expectedStatus}, errorMessage);
    }


    /**
     * Sends an HTTP request based on the provided method, path, and data.
     *
     * @param method HTTP method ("GET", "POST", "PUT", "PATCH", "DELETE")
     * @param path The URL path for the request
     * @param data The data to include in the request body (null if no data)
     * @param expectedStatuses An array of expected status codes. If the response status does not match any of these,
     *                         an exception will be thrown with the provided errorMessage
     * @param errorMessage The error message to use if an unexpected status code is received
     * @return The JSON response from the server as a JsonNode object
     */
    private JsonNode makeRequest(String method, String path, Map<String, Object> data, int[] expectedStatuses, String errorMessage)
    {
        path = path.replaceAll("^/+", "");
        Request.Builder builder = buildRequest(path);

        String body = null;
        if (data != null && !data.isEmpty())
        {
            body = Json.writeValueAsString(data);
        }

        switch (method.toUpperCase())
        {
            case "POST" ->
            {
                RequestBody requestBody = body != null
                    ? RequestBody.create(body, JSON_MEDIA_TYPE)
                    : RequestBody.create(new byte[0], null);
                builder.post(requestBody);
            }
            case "PATCH" ->
            {
                RequestBody requestBody = body != null
                    ? RequestBody.create(body, JSON_MEDIA_TYPE)
                    : RequestBody.create(new byte[0], null);
                builder.patch(requestBody);
            }
            case "PUT" ->
            {
                RequestBody requestBody = body != null
                    ? RequestBody.create(body, JSON_MEDIA_TYPE)
                    : RequestBody.create(new byte[0], null);
                builder.put(requestBody);
            }
            case "DELETE" -> builder.delete();
            default ->
            {
                HttpUrl.Builder urlBuilder = buildUrl(path).newBuilder();
                if (data != null) {
                    data.forEach((key, value) ->
                    {
                        if (value != null) {
                            urlBuilder.addQueryParameter(key, String.valueOf(value));
                        }
                    });
                }
                builder.url(urlBuilder.build());
                builder.get();
            }
        }

        log.debug("{} Request to {}/{}", method, endpoint, path);
        return executeRequest(builder.build(), expectedStatuses, errorMessage);
    }

    /**
     * Executes a network request and processes the response.
     *
     * @param request The HTTP request to execute.
     * @param expectedStatuses An array of expected HTTP status codes.
     * @param errorMessage A custom error message to use in case of an exception.
     * @return A JsonNode representing the parsed JSON response or null if no content is returned.
     * @throws FederationClientException If the HTTP response status code is not one of the expected values or if
     *         the response is not valid JSON when expected.
     */
    private JsonNode executeRequest(Request request, int[] expectedStatuses, String errorMessage)
    {
        try (Response response = httpClient.newCall(request).execute())
        {
            int statusCode = response.code();
            ResponseBody responseBody = response.body();
            String responseString = responseBody != null ? responseBody.string() : "";

            MediaType contentType = responseBody != null ? responseBody.contentType() : null;
            if (contentType != null && (!contentType.type().equals("application") || !contentType.subtype().equals("json")))
            {
                if (!responseString.trim().startsWith("{"))
                {
                    throw new FederationClientException(
                        errorMessage + ": Expected JSON response but received Content-Type: " + contentType,
                        statusCode);
                }
            }

            boolean isExpected = false;
            for (int expected : expectedStatuses)
            {
                if (statusCode == expected)
                {
                    isExpected = true;
                    break;
                }
            }

            if (!isExpected)
            {
                String msg = errorMessage + " received response code: " + statusCode;
                try
                {
                    JsonNode errorNode = Json.readTree(responseString);
                    if (errorNode.has("message"))
                    {
                        msg = errorMessage + ", " + errorNode.get("message").asText() + " received response code: " + statusCode;
                    }
                }
                catch (Exception e)
                {
                    log.debug("Failed to parse error response JSON", e);
                }

                throw new FederationClientException(msg, statusCode);
            }

            if (responseString.isBlank())
            {
                return Json.mapper().nullNode();
            }

            return Json.readTree(responseString);
        }
        catch (IOException e)
        {
            throw new FederationClientException(errorMessage + ": " + e.getMessage(), 0, e);
        }
    }

    /**
     * Returns the server information including version, feature flags, and record counts.
     *
     * @return A {@link ServerInformation} snapshot from the server
     */
    public ServerInformation getServerInformation()
    {
        JsonNode node = makeRequest("GET", "info", null, 200, "Failed to get server information");
        return Json.mapper().convertValue(node, ServerInformation.class);
    }

    /**
     * Returns the raw OpenAPI / JSON specification of the server.
     *
     * @return The specification as a {@link JsonNode} tree
     */
    public JsonNode getSpecification()
    {
        return makeRequest("GET", "specification", null, 200, "Failed to get specification");
    }

    /**
     * Searches across all record types with optional type filter and pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param types Optional list of record types to restrict the search to, or {@code null} for all types
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link SearchResult} entries matching the query
     * @throws IllegalArgumentException if the query is too short or pagination parameters are invalid
     */
    public List<SearchResult> search(String query, List<RecordType> types, int page, int limit)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (types != null && !types.isEmpty())
        {
            params.put("type", String.join(",", types.stream().map(RecordType::getValue).toList()));
        }

        JsonNode node = makeRequest("GET", "search", params, 200, "Failed to search, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Shortcut for {@link #search(String, List, int, int) search(query, null, 1, 10)}.
     *
     * @param query The search query (minimum 2 characters)
     * @return A list of {@link SearchResult} entries
     */
    public List<SearchResult> search(String query)
    {
        return search(query, null, 1, 10);
    }

    /**
     * Searches with record type filters using default pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param types The record types to restrict the search to, or {@code null} for all types
     * @return A list of {@link SearchResult} entries
     */
    public List<SearchResult> search(String query, List<RecordType> types)
    {
        return search(query, types, 1, 10);
    }

    /**
     * Searches without record type filters using explicit pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link SearchResult} entries
     */
    public List<SearchResult> search(String query, int page, int limit)
    {
        return search(query, null, page, limit);
    }

    /**
     * Scans content with no optional parameters.
     *
     * @param content The text content to scan (must not be empty)
     * @return A {@link ScannedContent} with the scan results
     */
    public ScannedContent scanContent(String content)
    {
        return scanContent(content, null, null, null, null);
    }

    /**
     * Scans content with an optional author identifier.
     *
     * @param content The text content to scan (must not be empty)
     * @param author The author entity identifier, or {@code null}
     * @return A {@link ScannedContent} with the scan results
     */
    public ScannedContent scanContent(String content, String author)
    {
        return scanContent(content, author, null, null, null);
    }

    /**
     * Scans content with an optional author identifier and top-K limit.
     *
     * @param content The text content to scan (must not be empty)
     * @param author The author entity identifier, or {@code null}
     * @param topK The maximum number of entity matches to return, or {@code null}
     * @return A {@link ScannedContent} with the scan results
     */
    public ScannedContent scanContent(String content, String author, Integer topK)
    {
        return scanContent(content, author, topK, null, null);
    }

    /**
     * Scans content with an optional author identifier, top-K limit, and confidence threshold.
     *
     * @param content The text content to scan (must not be empty)
     * @param author The author entity identifier, or {@code null}
     * @param topK The maximum number of entity matches to return, or {@code null}
     * @param threshold The classification confidence threshold, or {@code null}
     * @return A {@link ScannedContent} with the scan results
     */
    public ScannedContent scanContent(String content, String author, Integer topK, Float threshold)
    {
        return scanContent(content, author, topK, threshold, null);
    }

    /**
     * Scans content through the Federation content scanning system and returns the scan result
     * including resolved entities, classification, and risk score.
     *
     * @param content The text content to scan (must not be empty)
     * @param author The author entity identifier, or {@code null}
     * @param topK The maximum number of entity matches to return, or {@code null}
     * @param threshold The classification confidence threshold, or {@code null}
     * @param metadata Optional metadata to attach to the scan request
     * @return A {@link ScannedContent} with the scan results
     * @throws IllegalArgumentException if content is empty
     */
    public ScannedContent scanContent(String content, String author, Integer topK,
                                       Float threshold, Map<String, Object> metadata)
    {
        if (content == null || content.isEmpty())
        {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("content", content);
        if (author != null) params.put("author", author);
        if (topK != null) params.put("top_k", topK);
        if (threshold != null) params.put("threshold", threshold);
        if (metadata != null) params.put("metadata", metadata);

        JsonNode node = makeRequest("POST", "scan", params, 200, "Failed to scan content");
        return Json.mapper().convertValue(node, ScannedContent.class);
    }

    /**
     * Lists audit log entries with pagination.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listAuditLogs(int page, int limit)
    {
        return listAuditLogs(page, limit, null, null, null);
    }

    /**
     * Lists audit log entries using the default page (1) and limit (100).
     *
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listAuditLogs()
    {
        return listAuditLogs(1, 100, null, null, null);
    }

    /**
     * Lists audit log entries with pagination and an optional category filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listAuditLogs(int page, int limit, String category)
    {
        return listAuditLogs(page, limit, category, null, null);
    }

    /**
     * Lists audit log entries with pagination, an optional category filter, and a sort field.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listAuditLogs(int page, int limit, String category, String by)
    {
        return listAuditLogs(page, limit, category, by, null);
    }

    /**
     * Lists audit log entries with pagination, optional category filter, and sorting.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listAuditLogs(int page, int limit, String category, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "", params, 200,
                "Failed to list audit logs, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Retrieves a single audit log record by UUID.
     *
     * @param auditLogUuid The UUID of the audit log record
     * @return The {@link AuditLog} record
     */
    public AuditLog getAuditLogRecord(String auditLogUuid)
    {
        if (auditLogUuid == null || auditLogUuid.isEmpty())
        {
            throw new IllegalArgumentException("Audit log UUID cannot be empty");
        }

        JsonNode node = makeRequest("GET", "audit/" + auditLogUuid, null, 200,
            "Failed to get audit log record for UUID " + auditLogUuid);
        return Json.mapper().convertValue(node, AuditLog.class);
    }

    /**
     * Searches audit log entries by query text with pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of matching {@link AuditLog} entries
     */
    public List<AuditLog> searchAuditLogs(String query, int page, int limit)
    {
        return searchAuditLogs(query, page, limit, null, null, null);
    }

    /**
     * Searches audit log entries by query text with pagination and an optional category filter.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @return A list of matching {@link AuditLog} entries
     */
    public List<AuditLog> searchAuditLogs(String query, int page, int limit, String category)
    {
        return searchAuditLogs(query, page, limit, category, null, null);
    }

    /**
     * Searches audit log entries by query text with pagination, an optional category filter, and a sort field.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of matching {@link AuditLog} entries
     */
    public List<AuditLog> searchAuditLogs(String query, int page, int limit, String category, String by)
    {
        return searchAuditLogs(query, page, limit, category, by, null);
    }

    /**
     * Searches audit log entries by query text with pagination, optional category filter, and sorting.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of matching {@link AuditLog} entries
     */
    public List<AuditLog> searchAuditLogs(String query, int page, int limit, String category, String by, String order)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "audit/search", params, 200,
            "Failed to search audit logs, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Creates a new operator with the given name.
     *
     * @param operatorName The display name for the operator
     * @return The created operator containing its UUID and raw access token
     */
    public OperatorCreated createOperator(String operatorName)
    {
        if (operatorName == null || operatorName.isEmpty())
        {
            throw new IllegalArgumentException("Operator name cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", operatorName);

        JsonNode node = makeRequest("POST", "operators", params, 201,
            "Failed to create operator with name " + operatorName);
        return Json.mapper().convertValue(node, OperatorCreated.class);
    }

    /**
     * Deletes an operator by UUID.
     *
     * @param operatorUuid The UUID of the operator to delete
     */
    public void deleteOperator(String operatorUuid)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        makeRequest("DELETE", "operators/" + operatorUuid, null, 200,
            "Failed to delete operator with UUID " + operatorUuid);
    }

    /**
     * Disables an operator account.
     *
     * @param operatorUuid The UUID of the operator to disable
     */
    public void disableOperator(String operatorUuid)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        makeRequest("PATCH", "operators/" + operatorUuid + "/disable", null, 200,
            "Failed to disable operator with UUID " + operatorUuid);
    }

    /**
     * Enables a previously disabled operator account.
     *
     * @param operatorUuid The UUID of the operator to enable
     */
    public void enableOperator(String operatorUuid)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        makeRequest("PATCH", "operators/" + operatorUuid + "/enable", null, 200,
            "Failed to enable operator with UUID " + operatorUuid);
    }

    /**
     * Retrieves an operator record by UUID.
     *
     * @param operatorUuid The UUID of the operator
     * @return The {@link OperatorRecord}
     */
    public OperatorRecord getOperator(String operatorUuid)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        JsonNode node = makeRequest("GET", "operators/" + operatorUuid, null, 200, "Failed to get operator");
        return Json.mapper().convertValue(node, OperatorRecord.class);
    }

    /**
     * Retrieves the operator record for the currently authenticated operator.
     *
     * @return The {@link OperatorRecord} for the current operator
     */
    public OperatorRecord getSelf()
    {
        JsonNode node = makeRequest("GET", "operators/self", null, 200, "Failed to get self operator");
        return Json.mapper().convertValue(node, OperatorRecord.class);
    }

    /**
     * Lists operators with pagination.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link OperatorRecord} entries
     */
    public List<OperatorRecord> listOperators(int page, int limit)
    {
        return listOperators(page, limit, null, null, null);
    }

    /**
     * Lists operators using the default page (1) and limit (100).
     *
     * @return A list of {@link OperatorRecord} entries
     */
    public List<OperatorRecord> listOperators()
    {
        return listOperators(1, 100, null, null, null);
    }

    /**
     * Lists operators with pagination and an optional category filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ENABLED, DISABLED), or {@code null}
     * @return A list of {@link OperatorRecord} entries
     */
    public List<OperatorRecord> listOperators(int page, int limit, String category)
    {
        return listOperators(page, limit, category, null, null);
    }

    /**
     * Lists operators with pagination, an optional category filter, and a sort field.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ENABLED, DISABLED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link OperatorRecord} entries
     */
    public List<OperatorRecord> listOperators(int page, int limit, String category, String by)
    {
        return listOperators(page, limit, category, by, null);
    }

    /**
     * Lists operators with pagination, optional category filter, and sorting.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ENABLED, DISABLED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link OperatorRecord} entries
     */
    public List<OperatorRecord> listOperators(int page, int limit, String category, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "operators", params, 200,
            "Failed to list operators, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Lists audit log entries for a specific operator with pagination.
     *
     * @param operatorUuid The UUID of the operator
     * @param page         The page number (1-based)
     * @param limit        Items per page (minimum 1)
     * @return A list of {@link AuditLog} entries for the operator
     */
    public List<AuditLog> listOperatorAuditLogs(String operatorUuid, int page, int limit)
    {
        return listOperatorAuditLogs(operatorUuid, page, limit, null, null, null);
    }

    /**
     * Lists audit log entries for a specific operator with pagination and an optional category filter.
     *
     * @param operatorUuid The UUID of the operator
     * @param page         The page number (1-based)
     * @param limit        Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @return A list of {@link AuditLog} entries for the operator
     */
    public List<AuditLog> listOperatorAuditLogs(String operatorUuid, int page, int limit, String category)
    {
        return listOperatorAuditLogs(operatorUuid, page, limit, category, null, null);
    }

    /**
     * Lists audit log entries for a specific operator with pagination, an optional category filter, and a sort field.
     *
     * @param operatorUuid The UUID of the operator
     * @param page         The page number (1-based)
     * @param limit        Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link AuditLog} entries for the operator
     */
    public List<AuditLog> listOperatorAuditLogs(String operatorUuid, int page, int limit, String category, String by)
    {
        return listOperatorAuditLogs(operatorUuid, page, limit, category, by, null);
    }

    /**
     * Lists audit log entries for a specific operator with pagination, optional category filter, and sorting.
     *
     * @param operatorUuid The UUID of the operator
     * @param page         The page number (1-based)
     * @param limit        Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link AuditLog} entries for the operator
     */
    public List<AuditLog> listOperatorAuditLogs(String operatorUuid, int page, int limit, String category, String by, String order)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "operators/" + operatorUuid + "/audit", params, 200,
            "Failed to list audit logs for operator with UUID " + operatorUuid);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Lists evidence records submitted by a specific operator with pagination.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence in the results
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listOperatorEvidence(String operatorUuid, int page, int limit, boolean includeConfidential)
    {
        return listOperatorEvidence(operatorUuid, page, limit, includeConfidential, null, null);
    }

    /**
     * Lists evidence records submitted by a specific operator using default pagination (page 1, limit 100)
     * and excluding confidential evidence.
     *
     * @param operatorUuid The UUID of the operator
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listOperatorEvidence(String operatorUuid)
    {
        return listOperatorEvidence(operatorUuid, 1, 100, false, null, null);
    }

    /**
     * Lists evidence records submitted by a specific operator with pagination, excluding confidential evidence.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listOperatorEvidence(String operatorUuid, int page, int limit)
    {
        return listOperatorEvidence(operatorUuid, page, limit, false, null, null);
    }

    /**
     * Lists evidence records submitted by a specific operator with pagination, confidentiality filter, and a sort field.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence in the results
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listOperatorEvidence(String operatorUuid, int page, int limit, boolean includeConfidential, String by)
    {
        return listOperatorEvidence(operatorUuid, page, limit, includeConfidential, by, null);
    }

    /**
     * Lists evidence records submitted by a specific operator with pagination, confidentiality filter, and sorting.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence in the results
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listOperatorEvidence(String operatorUuid, int page, int limit, boolean includeConfidential, String by, String order)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("include_confidential", includeConfidential);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "operators/" + operatorUuid + "/evidence", params, 200,
            "Failed to list evidence records for operator with UUID " + operatorUuid);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Lists blacklist records created by a specific operator with pagination.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listOperatorBlacklist(String operatorUuid, int page, int limit, boolean includeLifted)
    {
        return listOperatorBlacklist(operatorUuid, page, limit, includeLifted, null, null);
    }

    /**
     * Lists blacklist records created by a specific operator using default pagination (page 1, limit 100)
     * and excluding lifted records.
     *
     * @param operatorUuid The UUID of the operator
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listOperatorBlacklist(String operatorUuid)
    {
        return listOperatorBlacklist(operatorUuid, 1, 100, false, null, null);
    }

    /**
     * Lists blacklist records created by a specific operator with pagination, excluding lifted records.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listOperatorBlacklist(String operatorUuid, int page, int limit)
    {
        return listOperatorBlacklist(operatorUuid, page, limit, false, null, null);
    }

    /**
     * Lists blacklist records created by a specific operator with pagination, lifted filter, and a sort field.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listOperatorBlacklist(String operatorUuid, int page, int limit, boolean includeLifted, String by)
    {
        return listOperatorBlacklist(operatorUuid, page, limit, includeLifted, by, null);
    }

    /**
     * Lists blacklist records created by a specific operator with pagination, lifted filter, and sorting.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listOperatorBlacklist(String operatorUuid, int page, int limit, boolean includeLifted, String by, String order)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("include_lifted", includeLifted);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "operators/" + operatorUuid + "/blacklist", params, 200,
            "Failed to list operator blacklist records with UUID " + operatorUuid);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Sets whether an operator has operator-level permissions.
     *
     * @param operatorUuid The UUID of the operator
     * @param hasOperatorPermissions {@code true} to grant operator permissions, {@code false} to revoke
     */
    public void setOperatorPermissions(String operatorUuid, boolean hasOperatorPermissions)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enabled", hasOperatorPermissions);

        makeRequest("PATCH", "operators/" + operatorUuid + "/operator-permissions", params, 200,
            "Failed to " + (hasOperatorPermissions ? "enable" : "disable") + " the operator's operator permissions");
    }

    /**
     * Sets whether an operator has client-level permissions.
     *
     * @param operatorUuid The UUID of the operator
     * @param hasClientPermissions {@code true} to grant client permissions, {@code false} to revoke
     */
    public void setClientPermissions(String operatorUuid, boolean hasClientPermissions)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enabled", hasClientPermissions);

        makeRequest("PATCH", "operators/" + operatorUuid + "/client-permissions", params, 200,
            "Failed to " + (hasClientPermissions ? "enable" : "disable") + " the operator's client permissions");
    }

    /**
     * Sets whether an operator has management-level permissions.
     *
     * @param operatorUuid The UUID of the operator
     * @param hasManagementPermissions {@code true} to grant management permissions, {@code false} to revoke
     */
    public void setManagementPermissions(String operatorUuid, boolean hasManagementPermissions)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enabled", hasManagementPermissions);

        makeRequest("PATCH", "operators/" + operatorUuid + "/management-permissions", params, 200,
            "Failed to " + (hasManagementPermissions ? "enable" : "disable") + " operator's management permission");
    }

    /**
     * Sets whether an operator is eligible to be automatically assigned reports generated by the host.
     *
     * @param operatorUuid The UUID of the operator
     * @param autoAssign {@code true} to enable automatic report assignment, {@code false} to disable
     */
    public void setAutoAssign(String operatorUuid, boolean autoAssign)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enabled", autoAssign);

        makeRequest("PATCH", "operators/" + operatorUuid + "/auto-assign", params, 200,
            "Failed to " + (autoAssign ? "enable" : "disable") + " the operator's auto assign");
    }

    /**
     * Generates a new access token for the current operator.
     *
     * @param update If {@code true}, the new token is immediately set on this client instance
     * @return The newly generated access token
     */
    public String generateAccessToken(boolean update)
    {
        JsonNode node = makeRequest("POST", "operators/refresh", null, 200, "Failed to generate Access token");
        String newToken = node.asText();

        if (update)
        {
            this.accessToken = newToken;
        }

        return newToken;
    }

    /**
     * Shortcut for {@link #generateAccessToken(boolean) generateAccessToken(true)}.
     *
     * @return The newly generated access token
     */
    public String generateAccessToken()
    {
        return generateAccessToken(true);
    }

    /**
     * Generates a new access token for a specific operator.
     *
     * @param operatorUuid The UUID of the operator
     * @return The newly generated access token for that operator
     */
    public String generateOperatorAccessToken(String operatorUuid)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        JsonNode node = makeRequest("POST", "operators/" + operatorUuid + "/refresh", null, 200,
            "Failed to generate Access token for operator with UUID " + operatorUuid);
        return node.asText();
    }

    /**
     * Searches operators by query text with pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of matching {@link OperatorRecord} entries
     */
    public List<OperatorRecord> searchOperators(String query, int page, int limit)
    {
        return searchOperators(query, page, limit, null, null, null);
    }

    /**
     * Searches operators by query text with pagination and an optional category filter.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ENABLED, DISABLED), or {@code null}
     * @return A list of matching {@link OperatorRecord} entries
     */
    public List<OperatorRecord> searchOperators(String query, int page, int limit, String category)
    {
        return searchOperators(query, page, limit, category, null, null);
    }

    /**
     * Searches operators by query text with pagination, an optional category filter, and a sort field.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ENABLED, DISABLED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of matching {@link OperatorRecord} entries
     */
    public List<OperatorRecord> searchOperators(String query, int page, int limit, String category, String by)
    {
        return searchOperators(query, page, limit, category, by, null);
    }

    /**
     * Searches operators by query text with pagination, optional category filter, and sorting.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ENABLED, DISABLED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of matching {@link OperatorRecord} entries
     */
    public List<OperatorRecord> searchOperators(String query, int page, int limit, String category, String by, String order)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "operators/search", params, 200,
            "Failed to search operators, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>() {
        });
    }

    /**
     * Updates the display name of an operator.
     *
     * @param operatorUuid The UUID of the operator
     * @param newName The new display name
     */
    public void updateOperatorName(String operatorUuid, String newName)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }
        if (newName == null || newName.isEmpty())
        {
            throw new IllegalArgumentException("Operator name cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", newName);

        makeRequest("PATCH", "operators/" + operatorUuid + "/update-name", params, 200,
            "Failed to update name for operator with UUID " + operatorUuid);
    }

    /**
     * Lists reports submitted by a specific operator with pagination and optional category filter.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter, or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOperatorReports(String operatorUuid, int page, int limit, String category)
    {
        return listOperatorReports(operatorUuid, page, limit, category, null, null);
    }

    /**
     * Lists reports submitted by a specific operator using default pagination (page 1, limit 100).
     *
     * @param operatorUuid The UUID of the operator
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOperatorReports(String operatorUuid)
    {
        return listOperatorReports(operatorUuid, 1, 100, null, null, null);
    }

    /**
     * Lists reports submitted by a specific operator with pagination.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOperatorReports(String operatorUuid, int page, int limit)
    {
        return listOperatorReports(operatorUuid, page, limit, null, null, null);
    }

    /**
     * Lists reports submitted by a specific operator with pagination, an optional category filter, and a sort field.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOperatorReports(String operatorUuid, int page, int limit, String category, String by)
    {
        return listOperatorReports(operatorUuid, page, limit, category, by, null);
    }

    /**
     * Lists reports submitted by a specific operator with pagination, optional category filter, and sorting.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOperatorReports(String operatorUuid, int page, int limit, String category, String by, String order)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "operators/" + operatorUuid + "/reports", params, 200,
            "Failed to list reports for operator " + operatorUuid);
        return Json.mapper().convertValue(node, new TypeReference<>() {
        });
    }

    /**
     * Lists reports assigned to a specific operator with pagination and optional category filter.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter, or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listAssignedOperatorReports(String operatorUuid, int page, int limit, String category)
    {
        return listAssignedOperatorReports(operatorUuid, page, limit, category, null, null);
    }

    /**
     * Lists reports assigned to a specific operator using default pagination (page 1, limit 100).
     *
     * @param operatorUuid The UUID of the operator
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listAssignedOperatorReports(String operatorUuid)
    {
        return listAssignedOperatorReports(operatorUuid, 1, 100, null, null, null);
    }

    /**
     * Lists reports assigned to a specific operator with pagination.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listAssignedOperatorReports(String operatorUuid, int page, int limit)
    {
        return listAssignedOperatorReports(operatorUuid, page, limit, null, null, null);
    }

    /**
     * Lists reports assigned to a specific operator with pagination, an optional category filter, and a sort field.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listAssignedOperatorReports(String operatorUuid, int page, int limit, String category, String by)
    {
        return listAssignedOperatorReports(operatorUuid, page, limit, category, by, null);
    }

    /**
     * Lists reports assigned to a specific operator with pagination, optional category filter, and sorting.
     *
     * @param operatorUuid The UUID of the operator
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listAssignedOperatorReports(String operatorUuid, int page, int limit, String category, String by, String order)
    {
        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "operators/" + operatorUuid + "/reports/assigned", params, 200,
            "Failed to list assigned reports for operator " + operatorUuid);
        return Json.mapper().convertValue(node, new TypeReference<>() {
        });
    }

    /**
     * Deletes an entity by UUID or host identifier.
     *
     * @param entityIdentifier The entity UUID or hostname
     */
    public void deleteEntity(String entityIdentifier)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        makeRequest("DELETE", "entities/" + entityIdentifier, null, 200,
            "Failed to delete the entity " + entityIdentifier);
    }

    /**
     * Retrieves an entity record by UUID, hostname, or SHA-256 hash.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @return The {@link EntityRecord}
     */
    public EntityRecord getEntityRecord(String entityIdentifier)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        JsonNode node = makeRequest("GET", "entities/" + entityIdentifier, null, 200,
            "Failed to get the entity record for " + entityIdentifier);
        return Json.mapper().convertValue(node, EntityRecord.class);
    }

    /**
     * Lists entities with pagination.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link EntityRecord} entries
     */
    public List<EntityRecord> listEntities(int page, int limit)
    {
        return listEntities(page, limit, null, null, null);
    }

    /**
     * Lists entities using the default page (1) and limit (100).
     *
     * @return A list of {@link EntityRecord} entries
     */
    public List<EntityRecord> listEntities()
    {
        return listEntities(1, 100, null, null, null);
    }

    /**
     * Lists entities with pagination and an optional category filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. WHITELISTED, WITH_RELATIONSHIP), or {@code null}
     * @return A list of {@link EntityRecord} entries
     */
    public List<EntityRecord> listEntities(int page, int limit, String category)
    {
        return listEntities(page, limit, category, null, null);
    }

    /**
     * Lists entities with pagination, an optional category filter, and a sort field.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. WHITELISTED, WITH_RELATIONSHIP), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link EntityRecord} entries
     */
    public List<EntityRecord> listEntities(int page, int limit, String category, String by)
    {
        return listEntities(page, limit, category, by, null);
    }

    /**
     * Lists entities with pagination, optional category filter, and sorting.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. WHITELISTED, WITH_RELATIONSHIP), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link EntityRecord} entries
     */
    public List<EntityRecord> listEntities(int page, int limit, String category, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "entities", params, 200,
            "Failed to list entities, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Retrieves the top-threat entities ranked by reputation.
     *
     * @return A list of {@link EntityRecord} entries for the top 10 threats
     */
    public List<EntityRecord> getTopThreats()
    {
        return getTopThreats(10);
    }

    /**
     * Retrieves the top-threat entities ranked by reputation.
     *
     * @param limit The maximum number of threats to return (minimum 1)
     * @return A list of {@link EntityRecord} entries for the top threats
     */
    public List<EntityRecord> getTopThreats(int limit)
    {
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);

        JsonNode node = makeRequest("GET", "entities/top-threats", params, 200,
            "Failed to retrieve top threats, limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Lists audit log entries for a specific entity with pagination.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listEntityAuditLogs(String entityIdentifier, int page, int limit)
    {
        return listEntityAuditLogs(entityIdentifier, page, limit, null, null, null);
    }

    /**
     * Lists audit log entries for a specific entity with pagination and an optional category filter.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listEntityAuditLogs(String entityIdentifier, int page, int limit, String category)
    {
        return listEntityAuditLogs(entityIdentifier, page, limit, category, null, null);
    }

    /**
     * Lists audit log entries for a specific entity with pagination, an optional category filter, and a sort field.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listEntityAuditLogs(String entityIdentifier, int page, int limit, String category, String by)
    {
        return listEntityAuditLogs(entityIdentifier, page, limit, category, by, null);
    }

    /**
     * Lists audit log entries for a specific entity with pagination, optional category filter, and sorting.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. OPERATOR_EVENTS, ENTITY_EVENTS), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link AuditLog} entries
     */
    public List<AuditLog> listEntityAuditLogs(String entityIdentifier, int page, int limit, String category, String by, String order)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "entities/" + entityIdentifier + "/audit", params, 200,
            "Failed to list audit logs for entity " + entityIdentifier);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Convenience method that lists all blacklist records for an entity (page 1, limit 100,
     * including lifted records).
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listEntityBlacklistRecords(String entityIdentifier)
    {
        return listEntityBlacklistRecords(entityIdentifier, 1, 100, true);
    }

    /**
     * Lists blacklist records for a specific entity with full pagination and lifted filter.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listEntityBlacklistRecords(String entityIdentifier, int page, int limit, boolean includeLifted)
    {
        return listEntityBlacklistRecords(entityIdentifier, page, limit, includeLifted, null, null);
    }

    /**
     * Lists blacklist records for a specific entity with pagination, excluding lifted records.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listEntityBlacklistRecords(String entityIdentifier, int page, int limit)
    {
        return listEntityBlacklistRecords(entityIdentifier, page, limit, false, null, null);
    }

    /**
     * Lists blacklist records for a specific entity with pagination, lifted filter, and a sort field.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listEntityBlacklistRecords(String entityIdentifier, int page, int limit, boolean includeLifted, String by)
    {
        return listEntityBlacklistRecords(entityIdentifier, page, limit, includeLifted, by, null);
    }

    /**
     * Lists blacklist records for a specific entity with pagination, lifted filter, and sorting.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listEntityBlacklistRecords(String entityIdentifier, int page, int limit, boolean includeLifted, String by, String order)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("include_lifted", includeLifted);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "entities/" + entityIdentifier + "/blacklist", params, 200,
            "Failed to list blacklist records for entity " + entityIdentifier);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Convenience method that lists all evidence records for an entity (page 1, limit 100,
     * including confidential).
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEntityEvidenceRecords(String entityIdentifier)
    {
        return listEntityEvidenceRecords(entityIdentifier, 1, 100, true);
    }

    /**
     * Lists evidence records for a specific entity with full pagination and confidentiality filter.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEntityEvidenceRecords(String entityIdentifier, int page, int limit, boolean includeConfidential)
    {
        return listEntityEvidenceRecords(entityIdentifier, page, limit, includeConfidential, null, null);
    }

    /**
     * Lists evidence records for a specific entity with pagination, excluding confidential evidence.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEntityEvidenceRecords(String entityIdentifier, int page, int limit)
    {
        return listEntityEvidenceRecords(entityIdentifier, page, limit, false, null, null);
    }

    /**
     * Lists evidence records for a specific entity with pagination, confidentiality filter, and a sort field.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEntityEvidenceRecords(String entityIdentifier, int page, int limit, boolean includeConfidential, String by)
    {
        return listEntityEvidenceRecords(entityIdentifier, page, limit, includeConfidential, by, null);
    }

    /**
     * Lists evidence records for a specific entity with pagination, confidentiality filter, and sorting.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEntityEvidenceRecords(String entityIdentifier, int page, int limit, boolean includeConfidential, String by, String order)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("include_confidential", includeConfidential);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "entities/" + entityIdentifier + "/evidence", params, 200,
            "Failed to list evidence records for entity " + entityIdentifier);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Lists reports associated with a specific entity with pagination and optional category filter.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter, or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listEntityReports(String entityIdentifier, int page, int limit, String category)
    {
        return listEntityReports(entityIdentifier, page, limit, category, null, null);
    }

    /**
     * Lists reports associated with a specific entity using default pagination (page 1, limit 100).
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listEntityReports(String entityIdentifier)
    {
        return listEntityReports(entityIdentifier, 1, 100, null, null, null);
    }

    /**
     * Lists reports associated with a specific entity with pagination.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listEntityReports(String entityIdentifier, int page, int limit)
    {
        return listEntityReports(entityIdentifier, page, limit, null, null, null);
    }

    /**
     * Lists reports associated with a specific entity with pagination, an optional category filter, and a sort field.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter, or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listEntityReports(String entityIdentifier, int page, int limit, String category, String by)
    {
        return listEntityReports(entityIdentifier, page, limit, category, by, null);
    }

    /**
     * Lists reports associated with a specific entity with pagination, optional category filter, and sorting.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listEntityReports(String entityIdentifier, int page, int limit, String category, String by, String order)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "entities/" + entityIdentifier + "/reports", params, 200,
            "Failed to list reports for entity " + entityIdentifier);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Pushes a new entity with only a hostname.
     *
     * @param host The entity hostname or IP address
     * @return The UUID of the created or existing entity
     */
    public String pushEntity(String host)
    {
        return pushEntity(host, null, null);
    }

    /**
     * Pushes a new entity with a hostname and optional user-level identifier.
     *
     * @param host The entity hostname or IP address
     * @param id   An optional user-level identifier, or {@code null}
     * @return The UUID of the created or existing entity
     */
    public String pushEntity(String host, String id)
    {
        return pushEntity(host, id, null);
    }

    /**
     * Pushes a new entity with a hostname, optional user-level identifier, and optional metadata.
     *
     * @param host The entity hostname or IP address
     * @param id An optional user-level identifier, or {@code null}
     * @param metadata Optional metadata key-value pairs
     * @return The UUID of the created or existing entity
     */
    public String pushEntity(String host, String id, Map<String, Object> metadata)
    {
        if (host == null || host.isEmpty())
        {
            throw new IllegalArgumentException("Host cannot be an empty string");
        }

        if (id != null && id.isEmpty())
        {
            throw new IllegalArgumentException("Entity ID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("host", host);
        params.put("id", id);
        if (metadata != null)
        {
            params.put("metadata", metadata);
        }

        JsonNode node = makeRequest("POST", "entities", params, new int[]{200, 201},
            "Failed to push entity with domain " + host);
        return node.asText();
    }

    /**
     * Updates the metadata of an existing entity. The provided metadata is merged with the
     * entity's existing metadata on the server.
     *
     * @param entityIdentifier The entity UUID, SHA-256 hash, or entity address (email) to update
     * @param metadata The metadata to merge with the existing entity metadata
     * @throws IllegalArgumentException if the entity identifier is empty or the metadata is {@code null}
     */
    public void updateEntity(String entityIdentifier, Map<String, Object> metadata)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        if (metadata == null)
        {
            throw new IllegalArgumentException("Metadata must not be null");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("metadata", metadata);

        makeRequest("PATCH", "entities/" + entityIdentifier, params, 200,
            "Failed to update entity " + entityIdentifier);
    }

    /**
     * Sets the whitelist state of an entity.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param whitelisted The new whitelist state
     */
    public void setEntityWhitelist(String entityIdentifier, boolean whitelisted)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("whitelisted", whitelisted);

        makeRequest("PATCH", "entities/" + entityIdentifier + "/whitelist", params, 200,
            "Failed to set whitelist state for entity " + entityIdentifier);
    }

    /**
     * Clears the reputation score for a specific entity.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     */
    public void clearEntityReputation(String entityIdentifier)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        makeRequest("PATCH", "entities/" + entityIdentifier + "/clear-reputation", null, 200,
            "Failed to clear reputation for entity " + entityIdentifier);
    }

    /**
     * Sets a relationship between two entities.
     *
     * @param entityIdentifier The source entity UUID, hostname, or hash
     * @param targetEntityUuid The UUID of the target entity
     * @param relationshipType The type of relationship to establish
     */
    public void setEntityRelationship(String entityIdentifier, String targetEntityUuid,
                                        EntityRelationshipType relationshipType)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        if (targetEntityUuid == null || targetEntityUuid.isEmpty())
        {
            throw new IllegalArgumentException("Target entity UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("target_entity_uuid", targetEntityUuid);
        params.put("relationship_type", relationshipType.getValue());

        makeRequest("PATCH", "entities/" + entityIdentifier + "/relationship", params, 200,
            "Failed to set relationship for entity " + entityIdentifier);
    }

    /**
     * Clears any existing relationship on the given entity.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     */
    public void clearEntityRelationship(String entityIdentifier)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        makeRequest("DELETE", "entities/" + entityIdentifier + "/relationship", null, 200,
            "Failed to clear relationship for entity " + entityIdentifier);
    }

    /**
     * Searches entities by query text with pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of matching {@link EntityRecord} entries
     */
    public List<EntityRecord> searchEntities(String query, int page, int limit)
    {
        return searchEntities(query, page, limit, null, null, null);
    }

    /**
     * Searches entities by query text with pagination and an optional category filter.
     *
     * @param query The search query (minimum 2 characters)
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. WHITELISTED, WITH_RELATIONSHIP), or {@code null}
     * @return A list of matching {@link EntityRecord} entries
     */
    public List<EntityRecord> searchEntities(String query, int page, int limit, String category)
    {
        return searchEntities(query, page, limit, category, null, null);
    }

    /**
     * Searches entities by query text with pagination, an optional category filter, and a sort field.
     *
     * @param query The search query (minimum 2 characters)
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. WHITELISTED, WITH_RELATIONSHIP), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of matching {@link EntityRecord} entries
     */
    public List<EntityRecord> searchEntities(String query, int page, int limit, String category, String by)
    {
        return searchEntities(query, page, limit, category, by, null);
    }

    /**
     * Searches entities by query text with pagination, optional category filter, and sorting.
     *
     * @param query The search query (minimum 2 characters)
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. WHITELISTED, WITH_RELATIONSHIP), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of matching {@link EntityRecord} entries
     */
    public List<EntityRecord> searchEntities(String query, int page, int limit, String category, String by, String order)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "entities/search", params, 200,
            "Failed to search entities, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>() {
        });
    }

    /**
     * Deletes an evidence record by UUID.
     *
     * @param evidenceUuid The UUID of the evidence record to delete
     */
    public void deleteEvidence(String evidenceUuid)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        makeRequest("DELETE", "evidence/" + evidenceUuid, null, 200,
            "Failed to delete evidence with UUID " + evidenceUuid);
    }

    /**
     * Retrieves an evidence record by UUID.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @return The {@link EvidenceRecord}
     */
    public EvidenceRecord getEvidenceRecord(String evidenceUuid)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        JsonNode node = makeRequest("GET", "evidence/" + evidenceUuid, null, 200,
            "Failed to get evidence record with UUID " + evidenceUuid);
        return Json.mapper().convertValue(node, EvidenceRecord.class);
    }

    /**
     * Lists all file attachments associated with an evidence record.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @return A list of {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> getEvidenceAttachments(String evidenceUuid)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        JsonNode node = makeRequest("GET", "evidence/" + evidenceUuid + "/attachments", null, 200,
            "Failed to get evidence attachments for evidence with UUID " + evidenceUuid);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Lists evidence records with pagination and confidentiality filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEvidence(int page, int limit, boolean includeConfidential)
    {
        return listEvidence(page, limit, includeConfidential, null, null, null);
    }

    /**
     * Lists evidence records using the default page (1), limit (100), excluding confidential evidence.
     *
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEvidence()
    {
        return listEvidence(1, 100, false, null, null, null);
    }

    /**
     * Lists evidence records with pagination, excluding confidential evidence.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEvidence(int page, int limit)
    {
        return listEvidence(page, limit, false, null, null, null);
    }

    /**
     * Lists evidence records with pagination, confidentiality filter, and an optional category filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence
     * @param category Optional category filter (e.g. CONFIDENTIAL, CLASSIFIED), or {@code null}
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEvidence(int page, int limit, boolean includeConfidential, String category)
    {
        return listEvidence(page, limit, includeConfidential, category, null, null);
    }

    /**
     * Lists evidence records with pagination, confidentiality filter, an optional category filter, and a sort field.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence
     * @param category Optional category filter (e.g. CONFIDENTIAL, CLASSIFIED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEvidence(int page, int limit, boolean includeConfidential, String category, String by)
    {
        return listEvidence(page, limit, includeConfidential, category, by, null);
    }

    /**
     * Lists evidence records with pagination, confidentiality filter, optional category filter, and sorting.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence
     * @param category Optional category filter (e.g. CONFIDENTIAL, CLASSIFIED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> listEvidence(int page, int limit, boolean includeConfidential, String category, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("include_confidential", includeConfidential);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "evidence", params, 200,
            "Failed to list evidence records, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Submits non-confidential evidence without content, note, or tag.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @return The UUID of the created evidence record
     */
    public String submitEvidence(String entityIdentifier)
    {
        return submitEvidence(entityIdentifier, null, null, null, false, null);
    }

    /**
     * Submits evidence with only a confidentiality flag.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param confidential Whether the evidence is confidential
     * @return The UUID of the created evidence record
     */
    public String submitEvidence(String entityIdentifier, boolean confidential)
    {
        return submitEvidence(entityIdentifier, null, null, null, confidential, null);
    }

    /**
     * Submits non-confidential evidence with only text content.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param textContent The evidence text content
     * @return The UUID of the created evidence record
     */
    public String submitEvidence(String entityIdentifier, String textContent)
    {
        return submitEvidence(entityIdentifier, textContent, null, null, false, null);
    }

    /**
     * Submits non-confidential evidence with text content and a note.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param textContent The evidence text content
     * @param note An optional note
     * @return The UUID of the created evidence record
     */
    public String submitEvidence(String entityIdentifier, String textContent, String note)
    {
        return submitEvidence(entityIdentifier, textContent, note, null, false, null);
    }

    /**
     * Submits non-confidential evidence without metadata.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param textContent The evidence text content
     * @param note An optional note
     * @param tag An optional tag
     * @return The UUID of the created evidence record
     */
    public String submitEvidence(String entityIdentifier, String textContent, String note, String tag)
    {
        return submitEvidence(entityIdentifier, textContent, note, tag, false, null);
    }

    /**
     * Submits evidence with confidentiality flag but no metadata.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param textContent The evidence text content
     * @param note An optional note
     * @param tag An optional tag
     * @param confidential Whether the evidence is confidential
     * @return The UUID of the created evidence record
     */
    public String submitEvidence(String entityIdentifier, String textContent, String note, String tag, boolean confidential)
    {
        return submitEvidence(entityIdentifier, textContent, note, tag, confidential, null);
    }

    /**
     * Submits evidence with full control over confidentiality and optional metadata.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash
     * @param textContent The evidence text content (may be {@code null})
     * @param note An optional note
     * @param tag An optional tag
     * @param confidential Whether the evidence is confidential
     * @param metadata Optional metadata key-value pairs
     * @return The UUID of the created evidence record
     */
    public String submitEvidence(String entityIdentifier, String textContent, String note, String tag, boolean confidential, Map<String, Object> metadata)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("Entity identifier cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("entity_identifier", entityIdentifier);
        params.put("confidential", confidential);
        if (textContent != null) params.put("text_content", textContent);
        if (note != null) params.put("note", note);
        if (tag != null) params.put("tag", tag);
        if (metadata != null) params.put("metadata", metadata);

        JsonNode node = makeRequest("POST", "evidence", params, 201,
            "Failed to submit evidence for entity " + entityIdentifier);
        return node.asText();
    }

    /**
     * Toggles the confidentiality flag on an evidence record.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @param confidential {@code true} to mark as confidential, {@code false} to make public
     */
    public void updateEvidenceConfidentiality(String evidenceUuid, boolean confidential)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("confidential", confidential);

        makeRequest("PATCH", "evidence/" + evidenceUuid + "/update-confidentiality", params, 200,
            "Failed to " + (confidential ? "set" : "unset") + " confidentiality for evidence with UUID " + evidenceUuid);
    }

    /**
     * Updates the tag on an evidence record.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @param tag The new tag value
     */
    public void updateEvidenceTag(String evidenceUuid, String tag)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        if (tag == null || tag.isEmpty())
        {
            throw new IllegalArgumentException("Tag cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tag", tag);

        makeRequest("PATCH", "evidence/" + evidenceUuid + "/update-tag", params, 200,
            "Failed to update tag for evidence with UUID " + evidenceUuid);
    }

    /**
     * Searches evidence records by query text with pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of matching {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> searchEvidence(String query, int page, int limit)
    {
        return searchEvidence(query, page, limit, null, null, null);
    }

    /**
     * Searches evidence records by query text with pagination and an optional category filter.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. CONFIDENTIAL, CLASSIFIED), or {@code null}
     * @return A list of matching {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> searchEvidence(String query, int page, int limit, String category)
    {
        return searchEvidence(query, page, limit, category, null, null);
    }

    /**
     * Searches evidence records by query text with pagination, an optional category filter, and a sort field.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. CONFIDENTIAL, CLASSIFIED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of matching {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> searchEvidence(String query, int page, int limit, String category, String by)
    {
        return searchEvidence(query, page, limit, category, by, null);
    }

    /**
     * Searches evidence records by query text with pagination, optional category filter, and sorting.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. CONFIDENTIAL, CLASSIFIED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of matching {@link EvidenceRecord} entries
     */
    public List<EvidenceRecord> searchEvidence(String query, int page, int limit, String category, String by, String order)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "evidence/search", params, 200,
            "Failed to search evidence, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>(){});
    }

    /**
     * Links an existing evidence record to a report.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @param reportUuid The UUID of the report
     */
    public void addEvidenceToReport(String evidenceUuid, String reportUuid)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        if (reportUuid == null || reportUuid.isEmpty())
        {
            throw new IllegalArgumentException("Report UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("report_uuid", reportUuid);

        makeRequest("PATCH", "evidence/" + evidenceUuid + "/link-report", params, 200,
            "Failed to link evidence " + evidenceUuid + " to report " + reportUuid);
    }

    /**
     * Deletes a report by UUID.
     *
     * @param reportUuid The UUID of the report to delete
     */
    public void deleteReport(String reportUuid)
    {
        if (reportUuid == null || reportUuid.isEmpty())
        {
            throw new IllegalArgumentException("Report UUID cannot be empty");
        }

        makeRequest("DELETE", "reports/" + reportUuid, null, 200,
            "Failed to delete report " + reportUuid);
    }

    /**
     * Submits a report with the minimum required fields.
     *
     * @param reportingEntity The entity UUID, hostname, or hash being reported
     * @param content The report content / evidence text
     * @param incidentType The type of security incident
     * @return A {@link ReportSubmission} containing the created report, evidence, and optional attachments
     */
    public ReportSubmission submitReport(String reportingEntity, String content, IncidentType incidentType)
    {
        return submitReport(reportingEntity, content, incidentType, null, null);
    }

    /**
     * Submits a report with an optional report message.
     *
     * @param reportingEntity The entity UUID, hostname, or hash being reported
     * @param content The report content / evidence text
     * @param incidentType The type of security incident
     * @param reportMessage An optional message attached to the report
     * @return A {@link ReportSubmission} containing the created report, evidence, and optional attachments
     */
    public ReportSubmission submitReport(String reportingEntity, String content, IncidentType incidentType, String reportMessage)
    {
        return submitReport(reportingEntity, content, incidentType, reportMessage, null);
    }

    /**
     * Submits a report with optional report message and evidence tag.
     *
     * @param reportingEntity The entity UUID, hostname, or hash being reported
     * @param content The report content / evidence text
     * @param incidentType The type of security incident
     * @param reportMessage An optional message attached to the report
     * @param evidenceTag An optional tag for the created evidence record
     * @return A {@link ReportSubmission} containing the created report, evidence, and optional attachments
     */
    public ReportSubmission submitReport(String reportingEntity, String content, IncidentType incidentType, String reportMessage,
                                            String evidenceTag)
    {
        if (reportingEntity == null || reportingEntity.isEmpty())
        {
            throw new IllegalArgumentException("Reporting entity identifier cannot be empty");
        }

        if (content == null || content.isEmpty())
        {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("reporting_entity", reportingEntity);
        params.put("content", content);
        params.put("incident_type", incidentType.getValue());

        if (reportMessage != null) params.put("report_message", reportMessage);
        if (evidenceTag != null) params.put("evidence_tag", evidenceTag);

        JsonNode node = makeRequest("POST", "reports", params, 200, "Failed to submit report");
        return Json.mapper().convertValue(node, ReportSubmission.class);
    }

    /**
     * Submits a report with optional report message, evidence tag, and local file attachments.
     * Each local file path is uploaded as a file attachment linked to the evidence record created
     * for the report.
     *
     * @param reportingEntity The entity UUID, hostname, or hash being reported
     * @param content The report content / evidence text
     * @param incidentType The type of security incident
     * @param reportMessage An optional message attached to the report
     * @param evidenceTag An optional tag for the created evidence record
     * @param localFilePaths Optional list of local file paths to attach to the report, or {@code null}
     * @return A {@link ReportSubmission} containing the created report, evidence, and optional attachments
     */
    public ReportSubmission submitReport(String reportingEntity, String content, IncidentType incidentType, String reportMessage,
                                            String evidenceTag, List<String> localFilePaths)
    {
        return submitReport(reportingEntity, content, incidentType, reportMessage, evidenceTag, localFilePaths, null);
    }

    /**
     * Submits a report with an optional report message, evidence tag, and attachments. Each local file
     * path is uploaded as a file attachment, and each remote URL is downloaded and uploaded as an
     * attachment (with a default maximum size of 50 MB), all linked to the evidence record created
     * for the report.
     *
     * @param reportingEntity The entity UUID, hostname, or hash being reported
     * @param content The report content / evidence text
     * @param incidentType The type of security incident
     * @param reportMessage An optional message attached to the report
     * @param evidenceTag An optional tag for the created evidence record
     * @param localFilePaths Optional list of local file paths to attach to the report, or {@code null}
     * @param remoteUrls Optional list of remote URLs to download and attach to the report, or {@code null}
     * @return A {@link ReportSubmission} containing the created report, evidence, and optional attachments
     */
    public ReportSubmission submitReport(String reportingEntity, String content, IncidentType incidentType, String reportMessage,
                                            String evidenceTag, List<String> localFilePaths, List<String> remoteUrls)
    {
        if (localFilePaths != null)
        {
            for (String path : localFilePaths)
            {
                if (path == null || path.isEmpty())
                {
                    throw new IllegalArgumentException("Each local file path must be a string");
                }
            }
        }

        if (remoteUrls != null)
        {
            for (String url : remoteUrls)
            {
                if (url == null || url.isEmpty())
                {
                    throw new IllegalArgumentException("Each remote URL must be a string");
                }
            }
        }

        ReportSubmission submission = submitReport(reportingEntity, content, incidentType, reportMessage, evidenceTag);

        List<UploadResult> attachments = new ArrayList<>();
        String evidenceUuid = submission.getEvidence().uuid();

        if (localFilePaths != null)
        {
            for (String localFilePath : localFilePaths)
            {
                attachments.add(uploadFileAttachment(evidenceUuid, localFilePath));
            }
        }

        if (remoteUrls != null)
        {
            for (String remoteUrl : remoteUrls)
            {
                attachments.add(uploadFileAttachmentFromUrl(evidenceUuid, remoteUrl, DEFAULT_MAX_FILE_SIZE));
            }
        }

        if (!attachments.isEmpty())
        {
            List<JsonNode> attachmentNodes = attachments.stream()
                .map(result -> (JsonNode) Json.mapper().valueToTree(result))
                .toList();

            return new ReportSubmission(submission.reportNode(), submission.evidenceNode(), attachmentNodes);
        }

        return submission;
    }

    /**
     * Lists reports with pagination and optional category filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter, or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listReports(int page, int limit, String category)
    {
        return listReports(page, limit, category, null, null);
    }

    /**
     * Lists reports using the default page (1), limit (100), and no category filter.
     *
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listReports()
    {
        return listReports(1, 100, null, null, null);
    }

    /**
     * Lists reports with pagination, an optional category filter, and a sort field.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listReports(int page, int limit, String category, String by)
    {
        return listReports(page, limit, category, by, null);
    }

    /**
     * Lists reports with pagination, optional category filter, and sorting.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listReports(int page, int limit, String category, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "reports", params, 200,
            "Failed to list reports, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {
        });
    }

    /**
     * Lists opened reports assigned to the currently authenticated operator with pagination.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOpenedReports(int page, int limit)
    {
        return listOpenedReports(page, limit, null, null);
    }

    /**
     * Lists opened reports assigned to the currently authenticated operator with pagination and a sort field.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOpenedReports(int page, int limit, String by)
    {
        return listOpenedReports(page, limit, by, null);
    }

    /**
     * Lists opened reports assigned to the currently authenticated operator with pagination and sorting.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link ReportRecord} entries
     */
    public List<ReportRecord> listOpenedReports(int page, int limit, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "reports/opened", params, 200,
            "Failed to list opened reports, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {
        });
    }

    /**
     * Retrieves a report by UUID.
     *
     * @param reportUuid The UUID of the report
     * @return The {@link ReportRecord}
     */
    public ReportRecord getReport(String reportUuid)
    {
        if (reportUuid == null || reportUuid.isEmpty())
        {
            throw new IllegalArgumentException("Report UUID cannot be empty");
        }

        JsonNode node = makeRequest("GET", "reports/" + reportUuid, null, 200,
            "Failed to get report " + reportUuid);
        return Json.mapper().convertValue(node, ReportRecord.class);
    }

    /**
     * Lists evidence records associated with a report using default pagination and excluding
     * confidential evidence.
     *
     * @param reportUuid The UUID of the report
     * @return A list of {@link EvidenceRecord} entries linked to the report
     */
    public List<EvidenceRecord> listReportEvidenceRecords(String reportUuid)
    {
        return listReportEvidenceRecords(reportUuid, 1, 100, false, null, null, null);
    }

    /**
     * Lists evidence records associated with a report with pagination and excluding confidential
     * evidence.
     *
     * @param reportUuid The UUID of the report
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link EvidenceRecord} entries linked to the report
     */
    public List<EvidenceRecord> listReportEvidenceRecords(String reportUuid, int page, int limit)
    {
        return listReportEvidenceRecords(reportUuid, page, limit, false, null, null, null);
    }

    /**
     * Lists evidence records associated with a report with pagination and an optional confidentiality
     * filter.
     *
     * @param reportUuid The UUID of the report
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence records
     * @return A list of {@link EvidenceRecord} entries linked to the report
     */
    public List<EvidenceRecord> listReportEvidenceRecords(String reportUuid, int page, int limit, boolean includeConfidential)
    {
        return listReportEvidenceRecords(reportUuid, page, limit, includeConfidential, null, null, null);
    }

    /**
     * Lists evidence records associated with a report with pagination, confidentiality filter, and an
     * optional category filter.
     *
     * @param reportUuid The UUID of the report
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence records
     * @param category Optional evidence category filter, or {@code null}
     * @return A list of {@link EvidenceRecord} entries linked to the report
     */
    public List<EvidenceRecord> listReportEvidenceRecords(String reportUuid, int page, int limit,
                                                           boolean includeConfidential, String category)
    {
        return listReportEvidenceRecords(reportUuid, page, limit, includeConfidential, category, null, null);
    }

    /**
     * Lists evidence records associated with a report with pagination, confidentiality filter,
     * optional category filter, and sorting.
     *
     * @param reportUuid The UUID of the report
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeConfidential Whether to include confidential evidence records
     * @param category Optional evidence category filter, or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link EvidenceRecord} entries linked to the report
     */
    public List<EvidenceRecord> listReportEvidenceRecords(String reportUuid, int page, int limit,
                                                           boolean includeConfidential, String category,
                                                           String by, String order)
    {
        if (reportUuid == null || reportUuid.isEmpty())
        {
            throw new IllegalArgumentException("Report UUID cannot be empty");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("include_confidential", includeConfidential);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "reports/" + reportUuid + "/evidence", params, 200,
            "Failed to list evidence records for report " + reportUuid);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Closes a report without a classification flag.
     *
     * @param reportUuid The UUID of the report to close
     */
    public void closeReport(String reportUuid)
    {
        closeReport(reportUuid, null);
    }

    /**
     * Closes a report with an optional classification flag.
     *
     * @param reportUuid The UUID of the report to close
     * @param classification The classification flag, or {@code null}
     */
    public void closeReport(String reportUuid, ClassificationFlag classification)
    {
        if (reportUuid == null || reportUuid.isEmpty())
        {
            throw new IllegalArgumentException("Report UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (classification != null)
        {
            params.put("classification_flag", classification.getValue());
            params.put("classification", classification.getValue());
        }

        makeRequest("PATCH", "reports/" + reportUuid + "/close", params, 200,
            "Failed to close report " + reportUuid);
    }

    /**
     * Assigns an operator to handle a report.
     *
     * @param reportUuid   The UUID of the report
     * @param operatorUuid The UUID of the operator to assign
     */
    public void assignOperatorToReport(String reportUuid, String operatorUuid)
    {
        if (reportUuid == null || reportUuid.isEmpty())
        {
            throw new IllegalArgumentException("Report UUID cannot be empty");
        }

        if (operatorUuid == null || operatorUuid.isEmpty())
        {
            throw new IllegalArgumentException("Operator UUID cannot be empty");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operator", operatorUuid);

        makeRequest("PATCH", "reports/" + reportUuid + "/assign", params, 200,
            "Failed to assign operator " + operatorUuid + " to report " + reportUuid);
    }

    /**
     * Searches reports by query text with pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of matching {@link ReportRecord} entries
     */
    public List<ReportRecord> searchReports(String query, int page, int limit)
    {
        return searchReports(query, page, limit, null, null, null);
    }

    /**
     * Searches reports by query text with pagination and an optional category filter.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @return A list of matching {@link ReportRecord} entries
     */
    public List<ReportRecord> searchReports(String query, int page, int limit, String category)
    {
        return searchReports(query, page, limit, category, null, null);
    }

    /**
     * Searches reports by query text with pagination, an optional category filter, and a sort field.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of matching {@link ReportRecord} entries
     */
    public List<ReportRecord> searchReports(String query, int page, int limit, String category, String by)
    {
        return searchReports(query, page, limit, category, by, null);
    }

    /**
     * Searches reports by query text with pagination, optional category filter, and sorting.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional incident category filter (e.g. OPENED, CLOSED, ASSIGNED), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of matching {@link ReportRecord} entries
     */
    public List<ReportRecord> searchReports(String query, int page, int limit, String category, String by, String order)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "reports/search", params, 200,
            "Failed to search reports, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Blacklists an entity with the given evidence and incident type, without an expiration
     * (permanent blacklist).
     *
     * @param entityIdentifier The entity UUID, hostname, or hash to blacklist
     * @param evidenceUuid The UUID of the supporting evidence record
     * @param type The incident type
     * @return The UUID of the created blacklist record
     */
    public String blacklistEntity(String entityIdentifier, String evidenceUuid, IncidentType type)
    {
        return blacklistEntity(entityIdentifier, evidenceUuid, type, null);
    }

    /**
     * Blacklists an entity with the given evidence, incident type, and optional expiration.
     *
     * @param entityIdentifier The entity UUID, hostname, or hash to blacklist
     * @param evidenceUuid The UUID of the supporting evidence record
     * @param type The incident type
     * @param expires The expiration timestamp (Unix epoch seconds), or {@code null} for permanent
     * @return The UUID of the created blacklist record
     */
    public String blacklistEntity(String entityIdentifier, String evidenceUuid, IncidentType type, Integer expires)
    {
        if (entityIdentifier == null || entityIdentifier.isEmpty())
        {
            throw new IllegalArgumentException("The entity identifier must not be empty");
        }

        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("The evidence UUID must not be empty");
        }

        if (expires != null && expires < 0)
        {
            throw new IllegalArgumentException("The expires parameter must be a positive integer or null");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("entity_identifier", entityIdentifier);
        params.put("evidence_uuid", evidenceUuid);
        params.put("type", type.getValue());
        params.put("expires", expires);

        JsonNode node = makeRequest("POST", "blacklist", params, 201,
            "Failed to blacklist entity " + entityIdentifier);
        return node.asText();
    }

    /**
     * Deletes a blacklist record by UUID.
     *
     * @param blacklistRecordUuid The UUID of the blacklist record to delete
     */
    public void deleteBlacklistRecord(String blacklistRecordUuid)
    {
        if (blacklistRecordUuid == null || blacklistRecordUuid.isEmpty())
        {
            throw new IllegalArgumentException("Blacklist record UUID cannot be empty");
        }

        makeRequest("DELETE", "blacklist/" + blacklistRecordUuid, null, 200,
            "Failed to delete blacklist record with UUID " + blacklistRecordUuid);
    }

    /**
     * Retrieves a blacklist record by UUID.
     *
     * @param blacklistRecordUuid The UUID of the blacklist record
     * @return The {@link BlacklistRecord}
     */
    public BlacklistRecord getBlacklistRecord(String blacklistRecordUuid)
    {
        if (blacklistRecordUuid == null || blacklistRecordUuid.isEmpty())
        {
            throw new IllegalArgumentException("Blacklist record UUID cannot be empty");
        }

        JsonNode node = makeRequest("GET", "blacklist/" + blacklistRecordUuid, null, 200,
            "Failed to get blacklist record with UUID " + blacklistRecordUuid);
        return Json.mapper().convertValue(node, BlacklistRecord.class);
    }

    /**
     * Lifts (deactivates) a blacklist record.
     *
     * @param blacklistRecordUuid The UUID of the blacklist record to lift
     */
    public void liftBlacklistRecord(String blacklistRecordUuid)
    {
        if (blacklistRecordUuid == null || blacklistRecordUuid.isEmpty())
        {
            throw new IllegalArgumentException("Blacklist record UUID cannot be empty");
        }

        makeRequest("PATCH", "blacklist/" + blacklistRecordUuid + "/lift", null, 200,
            "Failed to lift blacklist record with UUID " + blacklistRecordUuid);
    }

    /**
     * Extends the expiration of a blacklist record by a given number of seconds.
     *
     * @param blacklistRecordUuid The UUID of the blacklist record
     * @param seconds The number of seconds to extend by (must be positive)
     */
    public void extendBlacklistRecord(String blacklistRecordUuid, int seconds)
    {
        if (blacklistRecordUuid == null || blacklistRecordUuid.isEmpty())
        {
            throw new IllegalArgumentException("Blacklist record UUID cannot be empty");
        }

        if (seconds <= 0)
        {
            throw new IllegalArgumentException("Extension seconds must be positive");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("seconds", seconds);

        makeRequest("PATCH", "blacklist/" + blacklistRecordUuid + "/extend", params, 200,
            "Failed to extend blacklist record with UUID " + blacklistRecordUuid);
    }

    /**
     * Lists blacklist records with pagination and lifted filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listBlacklistRecords(int page, int limit, boolean includeLifted)
    {
        return listBlacklistRecords(page, limit, includeLifted, null, null, null);
    }

    /**
     * Lists blacklist records using the default page (1), limit (100), excluding lifted records.
     *
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listBlacklistRecords()
    {
        return listBlacklistRecords(1, 100, false, null, null, null);
    }

    /**
     * Lists blacklist records with pagination, excluding lifted records.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listBlacklistRecords(int page, int limit)
    {
        return listBlacklistRecords(page, limit, false, null, null, null);
    }

    /**
     * Lists blacklist records with pagination, lifted filter, and an optional category filter.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @param category Optional category filter (e.g. ACTIVE, EXPIRED, PERMANENT), or {@code null}
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listBlacklistRecords(int page, int limit, boolean includeLifted, String category)
    {
        return listBlacklistRecords(page, limit, includeLifted, category, null, null);
    }

    /**
     * Lists blacklist records with pagination, lifted filter, an optional category filter, and a sort field.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @param category Optional category filter (e.g. ACTIVE, EXPIRED, PERMANENT), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listBlacklistRecords(int page, int limit, boolean includeLifted, String category, String by)
    {
        return listBlacklistRecords(page, limit, includeLifted, category, by, null);
    }

    /**
     * Lists blacklist records with pagination, lifted filter, optional category filter, and sorting.
     *
     * @param page The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param includeLifted Whether to include lifted blacklist records
     * @param category Optional category filter (e.g. ACTIVE, EXPIRED, PERMANENT), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> listBlacklistRecords(int page, int limit, boolean includeLifted, String category, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        params.put("include_lifted", includeLifted);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "blacklist", params, 200,
            "Failed to list blacklist records, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {
        });
    }

    /**
     * Searches blacklist records by query text with pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of matching {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> searchBlacklist(String query, int page, int limit)
    {
        return this.searchBlacklist(query, page, limit, null, null, null);
    }

    /**
     * Searches blacklist records by query text with pagination and an optional category filter.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ACTIVE, EXPIRED, PERMANENT), or {@code null}
     * @return A list of matching {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> searchBlacklist(String query, int page, int limit, String category)
    {
        return searchBlacklist(query, page, limit, category, null, null);
    }

    /**
     * Searches blacklist records by query text with pagination, an optional category filter, and a sort field.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ACTIVE, EXPIRED, PERMANENT), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of matching {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> searchBlacklist(String query, int page, int limit, String category, String by)
    {
        return searchBlacklist(query, page, limit, category, by, null);
    }

    /**
     * Searches blacklist records by query text with pagination, optional category filter, and sorting.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. ACTIVE, EXPIRED, PERMANENT), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of matching {@link BlacklistRecord} entries
     */
    public List<BlacklistRecord> searchBlacklist(String query, int page, int limit, String category, String by, String order)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "blacklist/search", params, 200,
            "Failed to search blacklist records, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Retrieves metadata about a file attachment by UUID.
     *
     * @param attachmentUuid The UUID of the attachment
     * @return The {@link FileAttachmentRecord} with file metadata
     */
    public FileAttachmentRecord getAttachmentInfo(String attachmentUuid)
    {
        if (attachmentUuid == null || attachmentUuid.isEmpty())
        {
            throw new IllegalArgumentException("Attachment UUID cannot be empty");
        }

        JsonNode node = makeRequest("GET", "attachments/" + attachmentUuid + "/info", null, 200,
            "Failed to get attachment information with UUID " + attachmentUuid);
        return Json.mapper().convertValue(node, FileAttachmentRecord.class);
    }

    /**
     * Lists file attachments with pagination.
     *
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> listAttachments(int page, int limit)
    {
        return listAttachments(page, limit, null, null, null);
    }

    /**
     * Lists file attachments using the default page (1) and limit (100).
     *
     * @return A list of {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> listAttachments()
    {
        return listAttachments(1, 100, null, null, null);
    }

    /**
     * Lists file attachments with pagination and an optional category filter.
     *
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. IMAGE, DOCUMENT, ARCHIVE), or {@code null}
     * @return A list of {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> listAttachments(int page, int limit, String category)
    {
        return listAttachments(page, limit, category, null, null);
    }

    /**
     * Lists file attachments with pagination, an optional category filter, and a sort field.
     *
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. IMAGE, DOCUMENT, ARCHIVE), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> listAttachments(int page, int limit, String category, String by)
    {
        return listAttachments(page, limit, category, by, null);
    }

    /**
     * Lists file attachments with pagination, optional category filter, and sorting.
     *
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. IMAGE, DOCUMENT, ARCHIVE), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> listAttachments(int page, int limit, String category, String by, String order)
    {
        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "attachments", params, 200,
            "Failed to list attachments, page: " + page + ", limit: " + limit);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Deletes a file attachment by UUID.
     *
     * @param attachmentUuid The UUID of the attachment to delete
     */
    public void deleteAttachment(String attachmentUuid)
    {
        if (attachmentUuid == null || attachmentUuid.isEmpty())
        {
            throw new IllegalArgumentException("Attachment UUID cannot be empty");
        }

        makeRequest("DELETE", "attachments/" + attachmentUuid, null, 200,
            "Failed to delete attachment with UUID " + attachmentUuid);
    }

    /**
     * Downloads a file attachment to a local directory.
     *
     * @param attachmentUuid The UUID of the attachment to download
     * @param directoryPath  The local directory to save the file into (must exist and be writable)
     * @return The absolute path to the downloaded file
     */
    public String downloadAttachment(String attachmentUuid, String directoryPath)
    {
        if (attachmentUuid == null || attachmentUuid.isEmpty())
        {
            throw new IllegalArgumentException("Attachment UUID cannot be empty");
        }

        if (directoryPath == null || directoryPath.isEmpty())
        {
            throw new IllegalArgumentException("Directory path cannot be empty");
        }

        File dir = new File(directoryPath);
        if (!dir.exists())
        {
            throw new IllegalArgumentException("Directory does not exist: " + directoryPath);
        }

        if (!dir.canWrite())
        {
            throw new IllegalArgumentException("Directory is not writable: " + directoryPath);
        }

        String path = "attachments/" + attachmentUuid;
        path = path.replaceAll("^/+", "");
        HttpUrl url = buildUrl(path);

        Request.Builder builder = new Request.Builder().url(url).header("Accept", "application/octet-stream").get();

        String token = this.accessToken;

        if (token != null)
        {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = httpClient.newCall(builder.build()).execute())
        {
            int statusCode = response.code();
            if (statusCode != 200)
            {
                throw new FederationClientException("Failed to download attachment, HTTP code: " + statusCode, statusCode);
            }

            String filename = attachmentUuid;
            String contentDisposition = response.header("Content-Disposition");
            if (contentDisposition != null && !contentDisposition.isEmpty())
            {
                String[] parts = contentDisposition.split("filename=");
                if (parts.length > 1)
                {
                    filename = parts[1].replaceAll("[\"';]", "").trim();
                }
            }

            String finalFilePath = dir.getAbsolutePath() + File.separator + filename;
            ResponseBody body = response.body();
            if (body == null)
            {
                throw new FederationClientException("Failed to download attachment: empty response body", statusCode);
            }

            try (InputStream inputStream = body.byteStream(); FileOutputStream outputStream = new FileOutputStream(finalFilePath);
                 ReadableByteChannel inChannel = Channels.newChannel(inputStream);
                 FileChannel outChannel = outputStream.getChannel())
            {
                outChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
            }

            return finalFilePath;
        }
        catch (IOException e)
        {
            throw new FederationClientException("Failed to download attachment: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads a local file as an attachment to an evidence record, using the file's own name.
     *
     * @param evidenceUuid  The UUID of the evidence record
     * @param localFilePath The path to the local file
     * @return The {@link UploadResult} with the server-assigned UUID and URL
     */
    public UploadResult uploadFileAttachment(String evidenceUuid, String localFilePath)
    {
        Path path = Path.of(localFilePath);
        return uploadFileAttachment(evidenceUuid, localFilePath, path.getFileName().toString());
    }

    /**
     * Uploads a local file as an attachment with a custom file name.
     *
     * @param evidenceUuid  The UUID of the evidence record
     * @param localFilePath The path to the local file
     * @param fileName The file name to use on the server
     * @return The {@link UploadResult} with the server-assigned UUID and URL
     */
    public UploadResult uploadFileAttachment(String evidenceUuid, String localFilePath, String fileName)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        File file = new File(localFilePath);
        if (!file.exists())
        {
            throw new IllegalArgumentException("File does not exist: " + localFilePath);
        }

        if (!file.canRead())
        {
            throw new IllegalArgumentException("File is not readable: " + localFilePath);
        }

        if (file.length() == 0)
        {
            throw new IllegalArgumentException("Invalid file or empty file: " + localFilePath);
        }

        String path = "attachments";
        path = path.replaceAll("^/+", "");
        HttpUrl url = buildUrl(path);

        String uploadName = (fileName != null) ? fileName : file.getName();
        String mimeType = "application/octet-stream";
        try
        {
            String detected = Files.probeContentType(file.toPath());
            if (detected != null && !detected.isEmpty())
            {
                mimeType = detected;
            }
        }
        catch (IOException e)
        {
            log.debug("Failed to detect MIME type for {}", localFilePath, e);
        }
        RequestBody fileBody = RequestBody.create(file, MediaType.parse(mimeType));

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("evidence_uuid", evidenceUuid)
            .addFormDataPart("file", uploadName, fileBody);

        Request.Builder builder = new Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(multipartBuilder.build());

        String token = this.accessToken;
        if (token != null)
        {
            builder.header("Authorization", "Bearer " + token);
        }

        log.debug("POST Request to {}/{} for file upload", endpoint, path);

        try (Response response = httpClient.newCall(builder.build()).execute())
        {
            int statusCode = response.code();
            ResponseBody responseBody = response.body();
            String responseString = responseBody != null ? responseBody.string() : "";

            boolean isExpected = statusCode == 201;
            if (!isExpected)
            {
                String errorMsg = "File upload failed, received response code: " + statusCode;
                try
                {
                    JsonNode errorNode = Json.readTree(responseString);
                    if (errorNode.has("message"))
                    {
                        errorMsg = "File upload failed: " + errorNode.get("message").asText() + " (response code: " + statusCode + ")";
                    }
                }
                catch (Exception e)
                {
                    log.debug("Failed to parse upload error response JSON", e);
                }

                throw new FederationClientException(errorMsg, statusCode);
            }

            return Json.mapper().convertValue(Json.readTree(responseString), UploadResult.class);
        }
        catch (IOException e)
        {
            throw new FederationClientException("File upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads a text note as a .txt attachment to an evidence record. The content is written to a
     * temporary file, uploaded, and the temporary file is then deleted.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @param fileName The desired file name (".txt" extension is appended if missing)
     * @param content The text content of the note
     * @return The {@link UploadResult} with the server-assigned UUID and URL
     */
    public UploadResult uploadNoteAttachment(String evidenceUuid, String fileName, String content)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        if (fileName == null || fileName.isEmpty())
        {
            throw new IllegalArgumentException("File name cannot be empty");
        }

        if (content == null || content.isEmpty())
        {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        if (!fileName.endsWith(".txt"))
        {
            fileName += ".txt";
        }

        Path tempFile = null;

        try
        {
            tempFile = Files.createTempFile("fed_", ".txt");
            Files.writeString(tempFile, content);

            return uploadFileAttachment(evidenceUuid, tempFile.toAbsolutePath().toString(), fileName);
        }
        catch (IOException e)
        {
            throw new FederationClientException("Failed to create temporary file for note upload", e);
        }
        finally
        {
            if (tempFile != null)
            {
                try
                {
                    Files.deleteIfExists(tempFile);
                }
                catch (IOException e)
                {
                    log.warn("Failed to delete temporary file {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Downloads a file from a remote URL and uploads it as an attachment, using the default
     * maximum file size of 50 MB. The remote file is streamed through a temporary file which is
     * deleted after the upload completes.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @param fileUrl The URL of the remote file
     * @return The {@link UploadResult} with the server-assigned UUID and URL
     */
    public UploadResult uploadFileAttachmentFromUrl(String evidenceUuid, String fileUrl)
    {
        return uploadFileAttachmentFromUrl(evidenceUuid, fileUrl, DEFAULT_MAX_FILE_SIZE);
    }

    /**
     * Downloads a file from a remote URL and uploads it as an attachment. The remote file is
     * streamed through a temporary file which is deleted after the upload completes.
     *
     * @param evidenceUuid The UUID of the evidence record
     * @param fileUrl The URL of the remote file
     * @param maxFileSize The maximum allowed file size in bytes
     * @return The {@link UploadResult} with the server-assigned UUID and URL
     */
    public UploadResult uploadFileAttachmentFromUrl(String evidenceUuid, String fileUrl, int maxFileSize)
    {
        if (evidenceUuid == null || evidenceUuid.isEmpty())
        {
            throw new IllegalArgumentException("Evidence UUID cannot be empty");
        }

        if (fileUrl == null || fileUrl.isEmpty())
        {
            throw new IllegalArgumentException("Invalid URL provided: " + fileUrl);
        }

        if (maxFileSize <= 0)
        {
            throw new IllegalArgumentException("Maximum file size must be greater than 0");
        }

        log.debug("Downloading file from URL: {}", fileUrl);

        Path tempFile = null;

        try
        {
            tempFile = Files.createTempFile("federation_upload_", ".tmp");

            Request downloadRequest = new Request.Builder()
                .url(fileUrl)
                .header("User-Agent", "FederationLib/1.0 File Downloader")
                .build();

            try (Response downloadResponse = httpClient.newCall(downloadRequest).execute())
            {
                if (!downloadResponse.isSuccessful())
                {
                    throw new FederationClientException("Failed to download file from URL: " + fileUrl, downloadResponse.code());
                }

                ResponseBody downloadBody = downloadResponse.body();
                if (downloadBody == null)
                {
                    throw new FederationClientException("Empty response body from URL: " + fileUrl, 0);
                }

                long contentLength = downloadBody.contentLength();
                if (contentLength > maxFileSize)
                {
                    throw new FederationClientException("File exceeds maximum file size limit of " + maxFileSize + " bytes", 413);
                }

                Files.copy(downloadBody.byteStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            long actualSize = Files.size(tempFile);
            if (actualSize > maxFileSize)
            {
                throw new FederationClientException("Downloaded file size (" + actualSize + ") exceeds maximum file size limit of " + maxFileSize + " bytes", 413);
            }

            // Extract filename from URL
            String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || fileName.contains("?"))
            {
                fileName = "downloaded_file";
            }

            return uploadFileAttachment(evidenceUuid, tempFile.toAbsolutePath().toString(), fileName);
        }
        catch (IOException e)
        {
            throw new FederationClientException("Failed to upload file from URL: " + e.getMessage(), e);
        }
        finally
        {
            if (tempFile != null)
            {
                try
                {
                    Files.deleteIfExists(tempFile);
                }
                catch (IOException e)
                {
                    log.warn("Failed to delete temporary file {}", tempFile, e);
                }
            }
        }
    }

    /**
     * Searches file attachments by query text with pagination.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @return A list of matching {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> searchAttachments(String query, int page, int limit)
    {
        return searchAttachments(query, page, limit, null, null, null);
    }

    /**
     * Searches file attachments by query text with pagination and an optional category filter.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. IMAGE, DOCUMENT, ARCHIVE), or {@code null}
     * @return A list of matching {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> searchAttachments(String query, int page, int limit, String category)
    {
        return searchAttachments(query, page, limit, category, null, null);
    }

    /**
     * Searches file attachments by query text with pagination, an optional category filter, and a sort field.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. IMAGE, DOCUMENT, ARCHIVE), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @return A list of matching {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> searchAttachments(String query, int page, int limit, String category, String by)
    {
        return searchAttachments(query, page, limit, category, by, null);
    }

    /**
     * Searches file attachments by query text with pagination, optional category filter, and sorting.
     *
     * @param query The search query (minimum 2 characters)
     * @param page  The page number (1-based)
     * @param limit Items per page (minimum 1)
     * @param category Optional category filter (e.g. IMAGE, DOCUMENT, ARCHIVE), or {@code null}
     * @param by The field to sort by (case-insensitive), or {@code null}
     * @param order The sort direction ("ASC" or "DESC", case-insensitive), or {@code null}
     * @return A list of matching {@link FileAttachmentRecord} entries
     */
    public List<FileAttachmentRecord> searchAttachments(String query, int page, int limit, String category, String by, String order)
    {
        if (query.length() < 2)
        {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }

        if (page < 1) throw new IllegalArgumentException("Page must be greater than 0");
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("page", page);
        params.put("limit", limit);
        if (category != null) params.put("category", category);
        applySortParams(params, by, order);

        JsonNode node = makeRequest("GET", "attachments/search", params, 200,
            "Failed to search attachments, query: " + query);
        return Json.mapper().convertValue(node, new TypeReference<>() {});
    }

    /**
     * Applies sort parameters to the request params, normalising case to match the server's
     * expected format: {@code by} is lowercased and {@code order} is uppercased (e.g. "ASC"/"DESC").
     *
     * @param params The request parameters map to modify
     * @param by The field to sort by, or {@code null}
     * @param order The sort direction, or {@code null}
     */
    private static void applySortParams(Map<String, Object> params, String by, String order)
    {
        if (by != null)
        {
            params.put("by", by.toLowerCase(Locale.ROOT));
        }

        if (order != null)
        {
            params.put("order", order.toUpperCase(Locale.ROOT));
        }
    }
}
