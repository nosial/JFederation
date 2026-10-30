package net.nosial.jfederation.exceptions;

import java.io.Serial;
import java.time.Duration;
import java.util.Map;

/**
 * Thrown when a Federation API request fails. Wraps the HTTP status code and a human-readable
 * message that includes the standard HTTP reason phrase and any server-provided error detail.
 */
public class FederationClientException extends RuntimeException
{
    @Serial
    private static final long serialVersionUID = 1L;
    private final int statusCode;
    private final Duration retryAfter;

    private static final Map<Integer, String> ERROR_PREFIXES = Map.ofEntries(
        Map.entry(400, "Bad Request"),
        Map.entry(401, "Unauthorized"),
        Map.entry(403, "Forbidden"),
        Map.entry(404, "Not Found"),
        Map.entry(405, "Method Not Allowed"),
        Map.entry(408, "Request Timeout"),
        Map.entry(409, "Conflict"),
        Map.entry(413, "Payload Too Large"),
        Map.entry(415, "Unsupported Media Type"),
        Map.entry(422, "Unprocessable Entity"),
        Map.entry(429, "Too Many Requests"),
        Map.entry(500, "Internal Server Error"),
        Map.entry(502, "Bad Gateway"),
        Map.entry(503, "Service Unavailable"),
        Map.entry(504, "Gateway Timeout"),
        Map.entry(507, "Insufficient Storage")
    );

    /**
     * Constructs an exception with the given message and HTTP status code.
     *
     * @param message The error detail message
     * @param statusCode The HTTP status code returned by the server
     */
    public FederationClientException(String message, int statusCode)
    {
        this(message, statusCode, (Duration) null);
    }

    /**
     * Constructs an exception with the given message, HTTP status code, and the delay after which the server expects
     * to accept the request again (OFD 2.2 "Rate Limiting").
     *
     * @param message The error detail message
     * @param statusCode The HTTP status code returned by the server
     * @param retryAfter The delay before the request may be repeated, or {@code null} when the server gave none
     */
    public FederationClientException(String message, int statusCode, Duration retryAfter)
    {
        super(ERROR_PREFIXES.getOrDefault(statusCode, "HTTP " + statusCode) + ": " + message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    /**
     * Constructs an exception with the given message, HTTP status code, and cause.
     *
     * @param message The error detail message
     * @param statusCode The HTTP status code returned by the server
     * @param cause The underlying cause
     */
    public FederationClientException(String message, int statusCode, Throwable cause)
    {
        super(ERROR_PREFIXES.getOrDefault(statusCode, "HTTP " + statusCode) + ": " + message, cause);
        this.statusCode = statusCode;
        this.retryAfter = null;
    }

    /**
     * Constructs an exception with the given message and cause (no HTTP status code).
     *
     * @param message The error detail message
     * @param cause The underlying cause
     */
    public FederationClientException(String message, Throwable cause)
    {
        super(message, cause);
        this.statusCode = 0;
        this.retryAfter = null;
    }

    /**
     * Returns the HTTP status code that caused this exception.
     *
     * @return The status code, or 0 if no HTTP response was received
     */
    public int getStatusCode()
    {
        return statusCode;
    }

    /**
     * Returns the delay after which the server expects to accept the request again, given by the {@code retry_after}
     * member or the Retry-After header of an HTTP 429 or 503 error response (OFD 2.2 "Rate Limiting"). The request
     * must not be repeated, nor another request sent that the server would count against the same limit, before the
     * delay has elapsed. Once it has, the request may be repeated even when it is not idempotent, since the server did
     * not perform it.
     *
     * @return The delay, or {@code null} when the server gave none, as servers of earlier versions do
     */
    public Duration getRetryAfter()
    {
        return retryAfter;
    }

    /**
     * Returns whether the server rejected the request without performing it because it is rate limited (HTTP 429)
     * or temporarily unavailable (HTTP 503), so that it may be repeated later, after {@link #getRetryAfter()}.
     *
     * @return true if the status code is 429 or 503
     */
    public boolean isRetryable()
    {
        return statusCode == 429 || statusCode == 503;
    }
}
