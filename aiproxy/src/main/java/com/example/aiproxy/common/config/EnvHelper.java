package com.example.aiproxy.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnvHelper {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(EnvHelper.class.getName());

    private static String getEnvOrProperty(String key) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        return value;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = getEnvOrProperty(key);
        if (value != null && !value.isEmpty()) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    public static long getLong(String key, long defaultValue) {
        String value = getEnvOrProperty(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                LOGGER.warning("Failed to parse long for env/property key '" + key + "': " + value + ". Using default: " + defaultValue);
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public static int getInt(String key, int defaultValue) {
        String value = getEnvOrProperty(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOGGER.warning("Failed to parse int for env/property key '" + key + "': " + value + ". Using default: " + defaultValue);
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static double getDouble(String key, double defaultValue) {
        String value = getEnvOrProperty(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                LOGGER.warning("Failed to parse double for env/property key '" + key + "': " + value + ". Using default: " + defaultValue);
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static String getString(String key, String defaultValue) {
        String value = getEnvOrProperty(key);
        if (value != null) { // Allow empty string if set
            return value;
        }
        return defaultValue;
    }

    public static <T> T getJson(String key, T defaultValue, TypeReference<T> typeRef) {
        String jsonValue = getEnvOrProperty(key);
        if (jsonValue != null && !jsonValue.isEmpty()) {
            try {
                return objectMapper.readValue(jsonValue, typeRef);
            } catch (JsonProcessingException e) {
                LOGGER.log(Level.WARNING, "Failed to parse JSON for env/property key '" + key + "': " + jsonValue + ". Error: " + e.getMessage() + ". Using default.", e);
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    // Overloaded getJson for Map<String, ?> specifically if needed, though TypeReference is more general
    public static Map<String, Object> getJsonMap(String key, Map<String, Object> defaultValue) {
        return getJson(key, defaultValue, new TypeReference<Map<String, Object>>() {});
    }

    // Overloaded getJson for List<?>
     public static List<Object> getJsonList(String key, List<Object> defaultValue) {
        return getJson(key, defaultValue, new TypeReference<List<Object>>() {});
    }
}
