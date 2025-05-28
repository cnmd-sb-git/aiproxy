package com.example.aiproxy.common.mcpproxy;

import java.util.Optional;

/**
 * Defines the interface for managing session information.
 * A session typically maps a session ID to a backend endpoint or service instance.
 */
public interface SessionManager {

    /**
     * Generates and returns a new unique session ID.
     *
     * @return A new unique session ID.
     */
    String newSessionId();

    /**
     * Stores a session ID and its corresponding backend endpoint.
     * If the session ID already exists, its endpoint will be updated.
     *
     * @param sessionId The unique identifier for the session.
     * @param endpoint  The backend endpoint (e.g., host:port) associated with the session.
     */
    void setSession(String sessionId, String endpoint);

    /**
     * Retrieves the backend endpoint for a given session ID.
     *
     * @param sessionId The session ID to look up.
     * @return An {@link Optional} containing the endpoint if the session ID is found,
     *         otherwise an empty Optional.
     */
    Optional<String> getSessionEndpoint(String sessionId);

    /**
     * Removes a session ID and its associated endpoint from the store.
     * If the session ID does not exist, this operation has no effect.
     *
     * @param sessionId The session ID to delete.
     */
    void deleteSession(String sessionId);
}
