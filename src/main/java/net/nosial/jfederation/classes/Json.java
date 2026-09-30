package net.nosial.jfederation.classes;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility providing access to a pre-configured Jackson {@link ObjectMapper} and common
 * read/write operations. The mapper is configured with snake-case property naming, non-null
 * serialization, lenient unknown-property handling, and automatic module registration.
 */
public final class Json
{
    private static final Logger log = LoggerFactory.getLogger(Json.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .findAndAddModules()
            .build();

    private Json()
    {
    }

    /**
     * Returns the shared {@link ObjectMapper} instance.
     *
     * @return The configured object mapper
     */
    public static ObjectMapper mapper()
    {
        return MAPPER;
    }

    /**
     * Serializes an object to a JSON string.
     *
     * @param value The object to serialize
     * @return The JSON string representation
     * @throws RuntimeException if serialization fails
     */
    public static String writeValueAsString(Object value)
    {
        try
        {
            return MAPPER.writeValueAsString(value);
        }
        catch (JacksonException e)
        {
            log.error("Failed to serialize object of type {} to JSON", value.getClass().getName(), e);
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Deserializes a JSON string into an object of the specified type.
     *
     * @param content The JSON string
     * @param valueType The target class
     * @param <T> The target type
     * @return The deserialized object
     * @throws RuntimeException if deserialization fails
     */
    public static <T> T readValue(String content, Class<T> valueType)
    {
        try
        {
            return MAPPER.readValue(content, valueType);
        }
        catch (JacksonException e)
        {
            log.error("Failed to deserialize JSON to type {}", valueType.getName(), e);
            throw new RuntimeException("Failed to deserialize from JSON", e);
        }
    }

    /**
     * Parses a JSON string into a {@link JsonNode} tree.
     *
     * @param content The JSON string
     * @return The parsed JSON tree
     * @throws RuntimeException if parsing fails
     */
    public static JsonNode readTree(String content)
    {
        try
        {
            return MAPPER.readTree(content);
        }
        catch (JacksonException e)
        {
            log.error("Failed to parse JSON", e);
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
}
