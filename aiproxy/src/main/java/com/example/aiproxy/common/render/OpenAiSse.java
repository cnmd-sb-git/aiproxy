package com.example.aiproxy.common.render;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Represents a Server-Sent Event (SSE) formatted for OpenAI-like responses.
 * This class provides methods to construct and render such an event.
 */
public class OpenAiSse {

    private final String data;

    private static final String SSE_LINE_SEPARATOR = "\n\n";
    private static final String SSE_DATA_PREFIX = "data: ";
    // No need for byte array versions if using PrintWriter, but if writing bytes directly:
    // private static final byte[] SSE_LINE_SEPARATOR_BYTES = SSE_LINE_SEPARATOR.getBytes(StandardCharsets.UTF_8);
    // private static final byte[] SSE_DATA_PREFIX_BYTES = SSE_DATA_PREFIX.getBytes(StandardCharsets.UTF_8);


    /**
     * Constructs an OpenAISSE event with the given data.
     *
     * @param data The string data to be sent in the SSE event.
     */
    public OpenAiSse(String data) {
        this.data = data;
    }

    /**
     * Gets the data payload of this SSE event.
     * @return The data string.
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the standard SSE content type headers on the HttpServletResponse.
     * These headers are common for SSE streams.
     *
     * @param response The HttpServletResponse object.
     */
    public static void setSseHeaders(HttpServletResponse response) {
        response.setContentType("text/event-stream; charset=utf-8"); // Explicitly set charset
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        // "Transfer-Encoding: chunked" is usually handled by the servlet container automatically
        // when streaming data. Explicitly setting it might not be necessary or could interfere.
        // response.setHeader("Transfer-Encoding", "chunked"); 
        response.setHeader("X-Accel-Buffering", "no"); // Useful if behind nginx
    }

    /**
     * Renders this SSE event to the HttpServletResponse.
     * It sets the appropriate SSE headers and writes the event data in the
     * "data: ...\n\n" format.
     *
     * @param response The HttpServletResponse to write the event to.
     * @throws IOException If an error occurs while writing to the response.
     */
    public void render(HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) { // Only set headers if not already committed
            setSseHeaders(response);
        }

        PrintWriter writer = response.getWriter();
        
        writer.write(SSE_DATA_PREFIX);
        writer.write(this.data); // data itself should not contain newlines unless it's part of the multi-line data spec
        writer.write(SSE_LINE_SEPARATOR);
        
        if (writer.checkError()) { // Check for errors after write
            throw new IOException("Error occurred in PrintWriter while writing SSE event.");
        }
        // Flushing is important for SSE to ensure data is sent immediately.
        // The caller or a higher-level SSE utility might handle flushing strategy.
        // For a single event, flushing here is reasonable.
        response.flushBuffer(); 
    }
    
    /**
     * Static utility to render a simple data string as an SSE event.
     *
     * @param response The HttpServletResponse.
     * @param dataString The data to send.
     * @throws IOException If an I/O error occurs.
     */
    public static void sendEvent(HttpServletResponse response, String dataString) throws IOException {
        if (!response.isCommitted()) {
            setSseHeaders(response);
        }
        PrintWriter writer = response.getWriter();
        writer.write(SSE_DATA_PREFIX);
        writer.write(dataString);
        writer.write(SSE_LINE_SEPARATOR);
        if (writer.checkError()) {
            throw new IOException("Error occurred in PrintWriter while writing SSE event.");
        }
        response.flushBuffer();
    }
    
    /**
     * Static utility to send a more complex SSE event with event name, id, and data.
     *
     * @param response The HttpServletResponse.
     * @param eventName Optional event name.
     * @param eventId Optional event ID.
     * @param dataString The data to send.
     * @throws IOException If an I/O error occurs.
     */
    public static void sendEvent(HttpServletResponse response, String eventName, String eventId, String dataString) throws IOException {
        if (!response.isCommitted()) {
            setSseHeaders(response);
        }
        PrintWriter writer = response.getWriter();
        if (eventName != null && !eventName.isEmpty()) {
            writer.write("event: " + eventName + "\n");
        }
        if (eventId != null && !eventId.isEmpty()) {
            writer.write("id: " + eventId + "\n");
        }
        // Handle multi-line data if dataString contains newlines
        String[] lines = dataString.split("\n", -1);
        for (String line : lines) {
            writer.write(SSE_DATA_PREFIX + line + "\n");
        }
        writer.write("\n"); // End of event (extra newline)

        if (writer.checkError()) {
            throw new IOException("Error occurred in PrintWriter while writing SSE event.");
        }
        response.flushBuffer();
    }
}
