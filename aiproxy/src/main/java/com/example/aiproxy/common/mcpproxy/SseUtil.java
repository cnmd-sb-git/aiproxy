package com.example.aiproxy.common.mcpproxy;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SseUtil {

    private static final Logger LOGGER = Logger.getLogger(SseUtil.class.getName());
    private static final Duration DEFAULT_BACKEND_TIMEOUT = Duration.ofSeconds(60); // Timeout for backend requests
    private static final Duration CLIENT_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);


    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_BACKEND_TIMEOUT)
            .build();

    /**
     * Handles Server-Sent Events (SSE) proxying.
     * Connects to a backend SSE endpoint and streams events to the client.
     * Includes special handling for "event: endpoint" to manage session mapping.
     */
    public static void handleSse(
            HttpServletRequest clientRequest,
            HttpServletResponse clientResponse,
            SessionManager sessionManager,
            EndpointProvider endpointProvider,
            String backendUrl,
            Map<String, String> customHeadersToBackend) {

        HttpRequest backendRequest;
        try {
            backendRequest = HttpRequest.newBuilder()
                    .uri(new URI(backendUrl))
                    .GET()
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    // Copy custom headers
                    .headers(customHeadersToBackend.entrySet().stream()
                            .flatMap(entry -> List.of(entry.getKey(), entry.getValue()).stream())
                            .toArray(String[]::new))
                    .timeout(DEFAULT_BACKEND_TIMEOUT.plusSeconds(30)) // SSE connections can be long-lived
                    .build();
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Invalid backend URL syntax: " + backendUrl, e);
            sendError(clientResponse, "Invalid backend URL", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        try {
            HttpResponse<InputStream> backendResponse = httpClient.send(backendRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (backendResponse.statusCode() != HttpURLConnection.HTTP_OK) {
                LOGGER.warning("Backend SSE connection failed with status: " + backendResponse.statusCode() + " for URL: " + backendUrl);
                sendError(clientResponse, "Backend SSE connection failed", backendResponse.statusCode());
                return;
            }

            clientResponse.setContentType("text/event-stream");
            clientResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            clientResponse.setHeader("Cache-Control", "no-cache");
            clientResponse.setHeader("Connection", "keep-alive");
            clientResponse.setHeader("Access-Control-Allow-Origin", "*"); // Consider making this configurable
            // Other headers like X-Accel-Buffering: no if behind nginx

            // Start asynchronous processing if not already started
            final AsyncContext asyncContext = clientRequest.isAsyncStarted() ? clientRequest.getAsyncContext() : clientRequest.startAsync();
            asyncContext.setTimeout(0); // No timeout by the container, we manage it.

            final InputStream backendInputStream = backendResponse.body();
            final PrintWriter clientResponseWriter = clientResponse.getWriter();

            // Client disconnection handling
            asyncContext.addListener(new AsyncListener() {
                @Override public void onComplete(AsyncEvent event) throws IOException { backendInputStream.close(); }
                @Override public void onTimeout(AsyncEvent event) throws IOException { backendInputStream.close(); }
                @Override public void onError(AsyncEvent event) throws IOException { backendInputStream.close(); }
                @Override public void onStartAsync(AsyncEvent event) throws IOException { }
            });
            
            // Thread for proxying SSE data
            Thread sseProxyThread = new Thread(() -> {
                try (BufferedReader backendReader = new BufferedReader(new InputStreamReader(backendInputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = backendReader.readLine()) != null) {
                        if (clientResponse.isCommitted() && clientResponseWriter.checkError()) { // Check if client is still connected
                             LOGGER.info("Client disconnected or writer error, stopping SSE stream.");
                             break;
                        }
                        clientResponseWriter.write(line + "\n");

                        if (line.startsWith("event: endpoint")) {
                            String dataLine = backendReader.readLine(); // Expect "data: /path?sessionId=..."
                            if (dataLine == null) break; // End of stream
                             clientResponseWriter.write(dataLine + "\n"); // Forward original data line too

                            if (dataLine.startsWith("data: ")) {
                                String backendPath = dataLine.substring("data: ".length()).trim();
                                String newSessionId = sessionManager.newSessionId();
                                String clientFacingEndpoint = endpointProvider.newEndpoint(newSessionId);

                                // Construct full original backend endpoint
                                URL originalRequestUrl = new URL(backendUrl);
                                String fullBackendTarget = originalRequestUrl.getProtocol() + "://" + originalRequestUrl.getHost() +
                                        (originalRequestUrl.getPort() == -1 ? "" : ":" + originalRequestUrl.getPort()) + backendPath;
                                
                                sessionManager.setSession(newSessionId, fullBackendTarget);
                                LOGGER.fine("SSE 'endpoint' event: Mapped new session " + newSessionId + 
                                            " (client endpoint " + clientFacingEndpoint + 
                                            ") to backend " + fullBackendTarget);

                                // Send the modified data line to the client
                                clientResponseWriter.write("event: endpoint\n"); // Re-iterate event type for clarity if needed by client
                                clientResponseWriter.write("data: " + clientFacingEndpoint + "\n\n"); // Ensure extra newline for SSE event end
                                
                                // Schedule deletion of the session mapping after a timeout (e.g., if client doesn't use it)
                                // This is a simple way, a proper cleanup mechanism for unused sessions might be better.
                                // For now, matching Go's defer store.Delete(newSession) which happens on handler exit.
                                // Here, it's less clear when the 'handler' for this specific session ID exits.
                                // For now, we'll rely on the client to use it or it expires from SessionManager if it has TTL.
                                // The Go code's `defer store.Delete(newSession)` for SSEHandler is problematic
                                // as it would delete the session immediately after the SSE stream from the *initial*
                                // backend closes, not after the *newly established client session* ends.
                                // This part of the logic might need rethinking for robustness.
                            }
                        }
                        if (clientResponseWriter.checkError()) break; // Break if error writing to client
                        clientResponse.flushBuffer(); // Ensure data is sent to client
                    }
                } catch (IOException e) {
                    if (!isClientDisconnectException(e)) {
                        LOGGER.log(Level.WARNING, "Error proxying SSE stream: " + e.getMessage(), e);
                    } else {
                        LOGGER.info("Client disconnected during SSE streaming.");
                    }
                } finally {
                    try {
                        backendInputStream.close(); // Ensure backend stream is closed
                        if (asyncContext.getRequest().isAsyncStarted()) {
                             asyncContext.complete(); // Complete the async context
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error closing streams or completing async context: " + e.getMessage(), e);
                    }
                }
            });
            sseProxyThread.setName("sse-proxy-" + clientRequest.getRemoteAddr());
            sseProxyThread.start();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending backend SSE request or setting up client response: " + e.getMessage(), e);
            if (!clientResponse.isCommitted()) {
                 sendError(clientResponse, "Failed to connect to backend SSE service", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "SSE handling was interrupted: " + e.getMessage(), e);
             if (!clientResponse.isCommitted()) {
                sendError(clientResponse, "SSE request processing interrupted", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
    
    private static boolean isClientDisconnectException(IOException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("broken pipe") || msg.contains("connection reset") || msg.contains("client abort") || e.getClass().getName().contains("ClientAbortException");
    }


    /**
     * Handles general HTTP proxying for requests that are part of an SSE-related session.
     */
    public static void handleProxy(
            HttpServletRequest clientRequest,
            HttpServletResponse clientResponse,
            SessionManager sessionManager,
            EndpointProvider endpointProvider) {

        String clientRequestUrl = clientRequest.getRequestURL().toString() +
                                  (clientRequest.getQueryString() != null ? "?" + clientRequest.getQueryString() : "");
        
        String sessionId = endpointProvider.loadEndpoint(clientRequestUrl);
        if (sessionId == null || sessionId.isEmpty()) {
            sendError(clientResponse, "Missing or invalid session identifier in URL", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Optional<String> backendEndpointOpt = sessionManager.getSessionEndpoint(sessionId);
        if (backendEndpointOpt.isEmpty()) {
            sendError(clientResponse, "Invalid or expired session identifier", HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String backendTargetUrl = backendEndpointOpt.get();

        URI backendUri;
        try {
            backendUri = new URI(backendTargetUrl);
            if (!("http".equalsIgnoreCase(backendUri.getScheme()) || "https".equalsIgnoreCase(backendUri.getScheme()))) {
                 sendError(clientResponse, "Invalid backend endpoint scheme", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        } catch (URISyntaxException e) {
            sendError(clientResponse, "Invalid backend endpoint URL syntax", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        HttpRequest.Builder backendRequestBuilder = HttpRequest.newBuilder()
                .uri(backendUri)
                .method(clientRequest.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> {
                    try {
                        return clientRequest.getInputStream();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error getting client request input stream", e);
                        return InputStream.nullInputStream();
                    }
                }))
                .timeout(DEFAULT_BACKEND_TIMEOUT);

        // Copy headers from client request to backend request
        Enumeration<String> headerNames = clientRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            // Filter out headers like Host, or connection-specific ones if necessary
            if (headerName.equalsIgnoreCase("Host") || headerName.equalsIgnoreCase("Connection") || headerName.equalsIgnoreCase("Content-Length")) {
                continue;
            }
            Enumeration<String> headerValues = clientRequest.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                String headerValue = headerValues.nextElement();
                backendRequestBuilder.header(headerName, headerValue);
            }
        }
        // Set Host header for the backend
        backendRequestBuilder.header("Host", backendUri.getHost() + (backendUri.getPort() == -1 ? "" : ":" + backendUri.getPort()));


        try {
            HttpRequest backendRequest = backendRequestBuilder.build();
            HttpResponse<InputStream> backendResponse = httpClient.send(backendRequest, HttpResponse.BodyHandlers.ofInputStream());

            // Copy backend response headers to client response
            clientResponse.setStatus(backendResponse.statusCode());
            backendResponse.headers().map().forEach((headerName, headerValues) -> {
                // Filter out connection-specific headers
                if (headerName.equalsIgnoreCase("Transfer-Encoding") || 
                    headerName.equalsIgnoreCase("Connection") ||
                    headerName.equalsIgnoreCase("Content-Encoding") && headerValues.contains("gzip") // Let container handle if it was gzipped
                    ) {
                    return;
                }
                for (String value : headerValues) {
                    clientResponse.addHeader(headerName, value);
                }
            });

            // Copy backend response body to client response
            try (InputStream backendResponseBody = backendResponse.body();
                 OutputStream clientResponseWriter = clientResponse.getOutputStream()) {
                backendResponseBody.transferTo(clientResponseWriter);
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error proxying request for session " + sessionId + " to " + backendTargetUrl, e);
            if (!clientResponse.isCommitted()) {
                sendError(clientResponse, "Proxying error to backend", HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Proxy request for session " + sessionId + " was interrupted", e);
            if (!clientResponse.isCommitted()) {
                sendError(clientResponse, "Proxy request interrupted", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private static void sendError(HttpServletResponse response, String message, int statusCode) {
        try {
            response.sendError(statusCode, message);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error sending error response to client: " + e.getMessage(), e);
        }
    }

    private SseUtil() {
        // Private utility class
    }
}
