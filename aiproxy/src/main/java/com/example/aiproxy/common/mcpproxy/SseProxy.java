package com.example.aiproxy.common.mcpproxy;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Map;

/**
 * SseProxy handles proxying of Server-Sent Events (SSE) and related HTTP requests.
 * It uses a {@link SessionManager} to keep track of active sessions and an
 * {@link EndpointProvider} to manage client-facing versus backend-facing endpoint URLs.
 *
 * This class corresponds to the SSEAProxy struct in the Go implementation.
 */
public class SseProxy {

    private final SessionManager sessionManager;
    private final EndpointProvider endpointProvider;
    private final String backendUrl; // Default backend URL for initial SSE connection
    private final Map<String, String> customHeadersToBackend;

    /**
     * Creates a new SseProxy.
     *
     * @param backendUrl             The primary backend URL to which initial SSE connections are made.
     * @param customHeadersToBackend A map of custom headers to be added to requests sent to the backend.
     * @param sessionManager         The session manager implementation.
     * @param endpointProvider       The endpoint provider implementation.
     */
    public SseProxy(String backendUrl, Map<String, String> customHeadersToBackend,
                    SessionManager sessionManager, EndpointProvider endpointProvider) {
        if (backendUrl == null || backendUrl.isEmpty()) {
            throw new IllegalArgumentException("Backend URL cannot be null or empty.");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("SessionManager cannot be null.");
        }
        if (endpointProvider == null) {
            throw new IllegalArgumentException("EndpointProvider cannot be null.");
        }
        this.backendUrl = backendUrl;
        this.customHeadersToBackend = (customHeadersToBackend == null) ? Collections.emptyMap() : Collections.unmodifiableMap(customHeadersToBackend);
        this.sessionManager = sessionManager;
        this.endpointProvider = endpointProvider;
    }

    /**
     * Handles incoming client requests intended for the backend SSE stream.
     * It establishes a connection to the backend SSE endpoint and proxies events
     * back to the client. Special handling for "event: endpoint" is managed by SseUtil.
     *
     * @param clientRequest  The HttpServletRequest from the client.
     * @param clientResponse The HttpServletResponse to send SSE data to the client.
     */
    public void sseHandler(HttpServletRequest clientRequest, HttpServletResponse clientResponse) {
        SseUtil.handleSse(
                clientRequest,
                clientResponse,
                this.sessionManager,
                this.endpointProvider,
                this.backendUrl,
                this.customHeadersToBackend
        );
    }

    /**
     * Handles general HTTP requests that are part of an established SSE session.
     * It uses the session information (via EndpointProvider and SessionManager) to
     * determine the correct backend endpoint and proxies the request.
     *
     * @param clientRequest  The HttpServletRequest from the client.
     * @param clientResponse The HttpServletResponse to send the backend's response to.
     */
    public void proxyHandler(HttpServletRequest clientRequest, HttpServletResponse clientResponse) {
        SseUtil.handleProxy(
                clientRequest,
                clientResponse,
                this.sessionManager,
                this.endpointProvider
        );
    }
}
