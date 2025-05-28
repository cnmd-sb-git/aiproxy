package com.example.aiproxy.common.fastJSONSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides JSON serialization and deserialization utilities using Jackson.
 * This class aims to provide functionality similar to the Go `JSONSerializer`
 * which used the `bytedance/sonic` library, primarily for marshalling to JSON bytes
 * and unmarshalling from JSON bytes or strings.
 *
 * Note: The original Go implementation was designed as a GORM custom serializer.
 * This Java version provides general static utility methods for JSON conversion.
 */
public class FastJsonSerializer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(FastJsonSerializer.class.getName());

    static {
        // Configure ObjectMapper for behavior similar to typical JSON serializers if needed.
        // For example, if compatibility with `sonic` defaults is required, specific
        // Jackson features might need to be enabled/disabled.
        // Common configurations:
        // objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // The Go sonic library is generally fast and may have different defaults
        // than Jackson. For a "fast" serializer, one might also explore Jackson modules
        // like Afterburner for performance, but that's beyond a direct translation.
    }

    /**
     * Marshals a Java object into a JSON byte array.
     * Corresponds to the `Value` method in the Go GORM serializer which returned `(any, error)`,
     * where `any` was often `[]byte`.
     *
     * @param value The object to marshal.
     * @return A byte array containing the JSON representation of the object.
     * @throws JsonProcessingException If an error occurs during marshaling.
     */
    public static byte[] marshalToBytes(Object value) throws JsonProcessingException {
        if (value == null) {
            return null; // Or return "null".getBytes() if that's preferred for DB storage
        }
        return objectMapper.writeValueAsBytes(value);
    }

    /**
     * Marshals a Java object into a JSON string.
     *
     * @param value The object to marshal.
     * @return A string containing the JSON representation of the object.
     * @throws JsonProcessingException If an error occurs during marshaling.
     */
    public static String marshalToString(Object value) throws JsonProcessingException {
        if (value == null) {
            return "null"; // Consistent with how many JSON libraries serialize null
        }
        return objectMapper.writeValueAsString(value);
    }

    /**
     * Unmarshals JSON from a byte array into an instance of the specified class.
     * Corresponds to the `Scan` method in the Go GORM serializer.
     *
     * @param jsonBytes The byte array containing the JSON data.
     * @param clazz     The class to unmarshal the JSON into.
     * @param <T>       The type of the class.
     * @return An instance of the specified class, or null if jsonBytes is null or empty.
     * @throws IOException If an error occurs during unmarshalling (e.g., malformed JSON).
     */
    public static <T> T unmarshalFromBytes(byte[] jsonBytes, Class<T> clazz) throws IOException {
        if (jsonBytes == null || jsonBytes.length == 0) {
            // In Go's Scan, this would set the field to its zero value.
            // For Java objects, returning null is the closest equivalent for "empty" or "zero".
            return null;
        }
        try {
            return objectMapper.readValue(jsonBytes, clazz);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Failed to unmarshal JSON bytes: " + new String(jsonBytes, StandardCharsets.UTF_8), e);
            throw e;
        }
    }

    /**
     * Unmarshals JSON from a string into an instance of the specified class.
     *
     * @param jsonString The string containing the JSON data.
     * @param clazz      The class to unmarshal the JSON into.
     * @param <T>        The type of the class.
     * @return An instance of the specified class, or null if jsonString is null or empty.
     * @throws IOException If an error occurs during unmarshalling (e.g., malformed JSON).
     */
    public static <T> T unmarshalFromString(String jsonString, Class<T> clazz) throws IOException {
        if (jsonString == null || jsonString.isEmpty()) {
            // Similar to unmarshalFromBytes, returning null for empty/null input.
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, clazz);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Failed to unmarshal JSON string: " + jsonString, e);
            throw e;
        }
    }
    
    /**
     * Unmarshals JSON from various database value types (byte[], String) into an instance of the specified class.
     * This method more closely mirrors the GORM Scan method's input flexibility.
     *
     * @param dbValue The database value (can be byte[], String, or null).
     * @param clazz   The class to unmarshal the JSON into.
     * @param <T>     The type of the class.
     * @return An instance of the specified class, or null if dbValue is null or represents empty JSON.
     * @throws IOException If an error occurs during unmarshalling.
     * @throws IllegalArgumentException If dbValue is of an unsupported type.
     */
    public static <T> T unmarshalFromDbValue(Object dbValue, Class<T> clazz) throws IOException {
        if (dbValue == null) {
            return null;
        }

        byte[] jsonBytes;
        if (dbValue instanceof byte[]) {
            jsonBytes = (byte[]) dbValue;
        } else if (dbValue instanceof String) {
            jsonBytes = ((String) dbValue).getBytes(StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Failed to unmarshal JSON value: unsupported type " + dbValue.getClass().getName());
        }

        if (jsonBytes.length == 0) {
            return null;
        }
        
        try {
            return objectMapper.readValue(jsonBytes, clazz);
        } catch (JsonProcessingException e) {
            // It might be useful to log the original dbValue type for context here
            String originalValueForLog = (dbValue instanceof String) ? (String) dbValue : "byte_array_content";
            if (dbValue instanceof byte[] && jsonBytes.length < 200) { // Avoid logging huge byte arrays
                 originalValueForLog = new String(jsonBytes, StandardCharsets.UTF_8);
            } else if (dbValue instanceof String && ((String)dbValue).length() < 200){
                 originalValueForLog = (String)dbValue;
            } else if (dbValue instanceof String) {
                 originalValueForLog = ((String)dbValue).substring(0, Math.min(((String)dbValue).length(),200)) + "...";
            }


            LOGGER.log(Level.WARNING, "Failed to unmarshal JSON dbValue (" + dbValue.getClass().getSimpleName() + "): " + originalValueForLog, e);
            throw e;
        }
    }


    // Private constructor to prevent instantiation of this utility class.
    private FastJsonSerializer() {
    }
}
