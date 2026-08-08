package net.nosial.jfederation.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.nosial.jfederation.enums.NamedEntityType;

/**
 * Describes the position and type of a named entity within scanned text.
 *
 * @param offset the character offset where the entity starts in the scanned text
 * @param length the character length of the entity span
 * @param type the type of named entity (domain, URL, email, IP address, etc.)
 */
public record ResolvedEntityPosition(
    @JsonProperty("offset") int offset,
    @JsonProperty("length") int length,
    @JsonProperty("type") NamedEntityType type
) {
}
