package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The permission groups of an operator (OFD 2.0). Management permissions inherit client permissions, so
 * {@code client} is true whenever {@code management} is.
 *
 * @param client whether the operator holds client permissions
 * @param management whether the operator holds management permissions
 * @param operator whether the operator holds operator permissions
 */
public record OperatorPermissions(
    @JsonProperty("client") boolean client,
    @JsonProperty("management") boolean management,
    @JsonProperty("operator") boolean operator
) { }
