package com.example.aiproxy.common.render;

import com.example.aiproxy.common.fastJSONSerializer.FastJsonSerializer; // For ObjectMapper via marshalToString
import com.fasterxml.jackson.core.JsonProcessingException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter; // For direct SSE writing if OpenAiSse class is not used directly
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for rendering Server-Sent Events (SSE) responses.
 * This class provides methods analogous to those in the Go `render` package,
 * adapting them for a Java Servlet environment.
 */
public class RenderUtil {

    private static final Logger LOGGER = Logger.getLogger(RenderUtil.class.getName());

    /**
     * The standard SSE data payload indicating the end of a stream.
     */
    public static final String SSE_DONE_PAYLOAD = "[DONE]";

    /**
     * Sends a string data payload as a Server-Sent Event (SSE).
     * <p>
     * Note: In the original Go code, this function checked `gin.Context` for errors
     * or abortion. In this Java utility, such checks are expected to be handled
     * by the calling Servlet/Controller before invoking this method.
     * </p>
     *
     * @param response The HttpServletResponse to write the SSE to.
     * @param data     The string data to send.
     * @throws IOException If an error occurs while writing to the response.
     */
    public static void sendSseString(HttpServletResponse response, String data) throws IOException {
        if (response.isCommitted()) {
            LOGGER.warning("Response already committed. Cannot send SSE string data: " + data);
            return;
        }
        // Using the static sendEvent method from OpenAiSse for consistency
        OpenAiSse.sendEvent(response, data);
    }

    /**
     * Serializes an object to JSON and sends it as a Server-Sent Event (SSE) data payload.
     * <p>
     * Note: Similar to `sendSseString`, Gin-specific context checks are omitted.
     * Error handling for JSON serialization is included.
     * </p>
     *
     * @param response The HttpServletResponse to write the SSE to.
     * @param object   The object to serialize to JSON and send.
     * @throws IOException If an error occurs during JSON serialization or while writing to the response.
     */
    public static void sendSseObject(HttpServletResponse response, Object object) throws IOException {
        if (response.isCommitted()) {
            LOGGER.warning("Response already committed. Cannot send SSE object data.");
            return;
        }
        if (object == null) {
            // Decide behavior for null object: send "null" string, empty data, or nothing?
            // Go's sonic.Marshal(nil) might produce "null".
            // OpenAISSE(data: "null") is one option.
            sendSseString(response, "null"); 
            return;
        }

        String jsonData;
        try {
            // Using FastJsonSerializer's underlying ObjectMapper or its marshalToString method
            jsonData = FastJsonSerializer.marshalToString(object);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Error marshalling object to JSON for SSE: " + e.getMessage(), e);
            // Optionally, send an error SSE event or throw a more specific exception
            // For now, rethrow as IOException as per method signature
            throw new IOException("Error marshalling object to JSON for SSE", e);
        }
        
        OpenAiSse.sendEvent(response, jsonData);
    }

    /**
     * Sends a standard SSE "[DONE]" message, indicating the end of an event stream.
     *
     * @param response The HttpServletResponse to write the SSE to.
     * @throws IOException If an error occurs while writing to the response.
     */
    public static void sendSseDone(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            LOGGER.info("Response already committed. Cannot send SSE [DONE] message.");
            // Don't return, allow it to try and write, which will likely fail and be caught,
            // or the client might have disconnected, in which case it's fine.
            // Or, more robustly:
            // if (response.isCommitted()) { ... return; }
        }
        // Using the static sendEvent method from OpenAiSse for consistency
        OpenAiSse.sendEvent(response, SSE_DONE_PAYLOAD);
    }

    private RenderUtil() {
        // Private constructor for utility class
    }
}
