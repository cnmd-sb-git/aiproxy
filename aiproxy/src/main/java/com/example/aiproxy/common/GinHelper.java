package com.example.aiproxy.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
// Assuming Jackson library is used for JSON processing.
// Add to pom.xml:
// <dependency>
//   <groupId>com.fasterxml.jackson.core</groupId>
//   <artifactId>jackson-databind</artifactId>
//   <version>2.15.0</version> <!-- Use appropriate version -->
// </dependency>

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class GinHelper {

    public static final long MAX_REQUEST_BODY_SIZE = 1024 * 1024 * 50; // 50MB
    private static final String REQUEST_BODY_ATTRIBUTE = "REQUEST_BODY_BYTES";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> FORM_CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "application/x-www-form-urlencoded",
            "multipart/form-data"
    ));

    /**
     * Reads the request body from an HttpServletRequest.
     * This method attempts to cache the request body as a request attribute to allow
     * it to be read multiple times. If using a framework like Spring, consider using
     * ContentCachingRequestWrapper for more robust request body caching.
     *
     * @param request The HttpServletRequest.
     * @return A byte array containing the request body, or null if content type is form-data.
     * @throws IOException if an error occurs while reading the request body or if the body is too large.
     */
    public static byte[] getRequestBody(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType != null) {
            String lowerContentType = contentType.toLowerCase().split(";")[0].trim();
            if (FORM_CONTENT_TYPES.contains(lowerContentType)) {
                return null; // Don't process form data as raw body
            }
        }

        Object cachedBody = request.getAttribute(REQUEST_BODY_ATTRIBUTE);
        if (cachedBody != null) {
            return (byte[]) cachedBody;
        }

        long contentLength = request.getContentLengthLong();
        ServletInputStream inputStream = request.getInputStream();

        if (contentLength > MAX_REQUEST_BODY_SIZE) {
            throw new IOException("Request body too large: " + contentLength + ", max: " + MAX_REQUEST_BODY_SIZE);
        }

        byte[] bodyBytes;
        if (contentLength > 0) { // If content length is known and valid
            bodyBytes = new byte[(int) contentLength];
            int bytesRead = 0;
            int offset = 0;
            while (bytesRead != -1 && offset < contentLength) {
                bytesRead = inputStream.read(bodyBytes, offset, (int) (contentLength - offset));
                if (bytesRead != -1) {
                    offset += bytesRead;
                }
            }
            if (offset < contentLength) {
                 throw new IOException("Request body read incomplete. Expected " + contentLength + " bytes, got " + offset);
            }
        } else { // Content length unknown or -1 (e.g., chunked encoding)
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (LimitedReader limitedInputStream = new LimitedReader(inputStream, MAX_REQUEST_BODY_SIZE)) {
                byte[] data = new byte[4096];
                int bytesRead;
                while ((bytesRead = limitedInputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }
                if (limitedInputStream.hasExceeded()) {
                     throw new IOException("Request body too large, max: " + MAX_REQUEST_BODY_SIZE + " (chunked)");
                }
            } catch (IOException e) {
                if (LimitedReader.ERR_LIMITED_READER_EXCEEDED.equals(e.getMessage())) {
                    throw new IOException("Request body too large, max: " + MAX_REQUEST_BODY_SIZE, e);
                }
                throw e; // Re-throw other IOExceptions
            }
            bodyBytes = buffer.toByteArray();
        }

        request.setAttribute(REQUEST_BODY_ATTRIBUTE, bodyBytes);
        return bodyBytes;
    }

    /**
     * Unmarshals the request body into an object of the specified class.
     * Assumes the request body is JSON.
     *
     * @param request The HttpServletRequest.
     * @param clazz   The class to unmarshal the JSON into.
     * @param <T>     The type of the class.
     * @return An instance of the specified class.
     * @throws IOException if an error occurs reading or parsing the request body.
     */
    public static <T> T unmarshalBodyReusable(HttpServletRequest request, Class<T> clazz) throws IOException {
        byte[] requestBody = getRequestBody(request);
        if (requestBody == null || requestBody.length == 0) {
            // Return null or throw exception based on how empty bodies should be handled
            // For example, if the target type is expecting an empty object or if it's an error.
            // sonic.Unmarshal might handle empty byte array differently, Jackson typically would error
            // or return null depending on configuration for certain types.
            // Let's try to create a default instance or return null if that's not possible.
            try {
                return objectMapper.readValue("{}", clazz); // Or handle as error/null
            } catch (JsonProcessingException e) {
                 // if clazz is not a simple bean, this might fail.
                 // Consider returning null or a custom "empty" object.
                return null;
            }
        }
        return objectMapper.readValue(requestBody, clazz);
    }

    /**
     * Unmarshals the request body into a JsonNode (Jackson's equivalent of sonic.ast.Node).
     * Assumes the request body is JSON.
     *
     * @param request The HttpServletRequest.
     * @return A JsonNode representing the root of the JSON structure.
     * @throws IOException if an error occurs reading or parsing the request body.
     */
    public static JsonNode unmarshalBodyToNode(HttpServletRequest request) throws IOException {
        byte[] requestBody = getRequestBody(request);
        if (requestBody == null || requestBody.length == 0) {
            return objectMapper.createObjectNode(); // Return empty node for empty body
        }
        return objectMapper.readTree(requestBody);
    }

    // Private constructor to prevent instantiation
    private GinHelper() {
    }
}
