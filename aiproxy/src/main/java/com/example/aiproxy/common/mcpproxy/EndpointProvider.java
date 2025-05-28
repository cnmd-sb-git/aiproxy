package com.example.aiproxy.common.mcpproxy;

/**
 * Defines an interface for managing the creation and interpretation of
 * session-specific endpoints.
 * This is used in proxying scenarios where a client-facing endpoint needs to be
 * mapped to a session ID, and vice-versa, or where new client-facing endpoints
 * are generated for new sessions.
 */
public interface EndpointProvider {

    /**
     * Generates a new client-facing endpoint string for a given new session ID.
     * The format of this endpoint string is implementation-specific but should
     * be something the client can use for subsequent requests.
     *
     * @param newSessionId The newly created session ID.
     * @return A string representing the new client-facing endpoint.
     */
    String newEndpoint(String newSessionId);

    /**
     * Loads or extracts a session ID from a given client-facing endpoint string.
     * This is the reverse of {@link #newEndpoint(String)}.
     *
     * @param endpointString The client-facing endpoint string (e.g., from a request URL).
     * @return The session ID extracted from the endpoint string, or null/empty if not found or invalid.
     */
    String loadEndpoint(String endpointString);
}
