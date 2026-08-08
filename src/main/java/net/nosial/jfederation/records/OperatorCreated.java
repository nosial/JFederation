package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result returned when creating a new operator. Contains the operator's UUID and the raw
 * access token, which is only exposed at creation time (and by token refresh endpoints); it is
 * never returned on {@link OperatorRecord}.
 *
 * @param uuid the unique identifier for the created operator
 * @param accessToken the raw access token for the created operator
 */
public record OperatorCreated(
    @JsonProperty("uuid") String uuid,
    @JsonProperty("access_token") String accessToken
) { }
