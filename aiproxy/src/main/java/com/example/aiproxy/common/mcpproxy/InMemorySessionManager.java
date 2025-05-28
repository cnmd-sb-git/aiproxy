package com.example.aiproxy.common.mcpproxy;

import com.example.aiproxy.common.Utils; // For ShortUUID

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Map;
import java.util.HashMap;


/**
 * An in-memory implementation of the {@link SessionManager} interface.
 * This class uses a {@link ConcurrentHashMap} for thread-safe session storage.
 */
public class InMemorySessionManager implements SessionManager {

    // Using ConcurrentHashMap for thread-safe operations without explicit locking for simple get/set/delete.
    private final ConcurrentHashMap<String, String> sessions;

    /**
     * Creates a new InMemorySessionStore.
     */
    public InMemorySessionManager() {
        this.sessions = new ConcurrentHashMap<>();
    }

    /**
     * Generates a new unique session ID using a utility function.
     *
     * @return A new unique session ID.
     */
    @Override
    public String newSessionId() {
        // Assuming Utils.shortUUID() is the translated equivalent of common.ShortUUID()
        return Utils.shortUUID();
    }

    /**
     * Stores a session ID and its corresponding backend endpoint.
     * If the session ID already exists, its endpoint will be updated.
     *
     * @param sessionId The unique identifier for the session.
     * @param endpoint  The backend endpoint (e.g., host:port) associated with the session.
     */
    @Override
    public void setSession(String sessionId, String endpoint) {
        if (sessionId == null || endpoint == null) {
            // Or throw IllegalArgumentException, depending on desired contract
            return; 
        }
        sessions.put(sessionId, endpoint);
    }

    /**
     * Retrieves the backend endpoint for a given session ID.
     *
     * @param sessionId The session ID to look up.
     * @return An {@link Optional} containing the endpoint if the session ID is found,
     *         otherwise an empty Optional.
     */
    @Override
    public Optional<String> getSessionEndpoint(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Removes a session ID and its associated endpoint from the store.
     * If the session ID does not exist, this operation has no effect.
     *
     * @param sessionId The session ID to delete.
     */
    @Override
    public void deleteSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
