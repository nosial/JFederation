package net.nosial.jfederation.classes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility providing access to a pre-configured Jackson {@link ObjectMapper} and common
 * read/write operations. The mapper is configured with snake-case property naming, non-null
 * serialisation, lenient unknown-property handling, and automatic module registration.
 */
public final class Json
{
    private static final Logger log = LoggerFactory.getLogger(Json.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

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
     * Serialises an object to a JSON string.
     *
     * @param value The object to serialise
     * @return The JSON string representation
     * @throws RuntimeException if serialisation fails
     */
    public static String writeValueAsString(Object value)
    {
        try
        {
            return MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException e)
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
     * @return The deserialised object
     * @throws RuntimeException if deserialisation fails
     */
    public static <T> T readValue(String content, Class<T> valueType)
    {
        try
        {
            return MAPPER.readValue(content, valueType);
        }
        catch (JsonProcessingException e)
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
        catch (JsonProcessingException e)
        {
            log.error("Failed to parse JSON", e);
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
}
