package com.example.aiproxy.common.ipblack;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * In-memory implementation for IP blacklisting.
 * This class provides static methods to manage a blacklist in memory.
 */
public class InMemoryIpBlacklist {

    private static final Logger LOGGER = Logger.getLogger(InMemoryIpBlacklist.class.getName());
    private static final ConcurrentHashMap<String, Instant> ipBlackMap = new ConcurrentHashMap<>();

    /**
     * Adds or updates an IP address in the in-memory blacklist with a new expiry time.
     * If the IP is already blacklisted and its current blacklist entry has not expired,
     * the existing expiry time is maintained (not extended by the new duration).
     * If the IP is not present, or if its existing entry has expired, a new entry
     * is created/updated with the new expiry time.
     *
     * @param ip       The IP address to blacklist.
     * @param duration The duration for which the IP should be blacklisted from now.
     *                 A non-positive duration will result in the IP not being added
     *                 or an expired entry effectively being set (and likely immediately removable).
     */
    public static void setIpBlack(String ip, Duration duration) {
        if (ip == null || ip.isEmpty()) {
            LOGGER.warning("IP address for in-memory blacklist cannot be null or empty.");
            return;
        }
        if (duration == null) {
            LOGGER.warning("Duration for in-memory blacklist cannot be null for IP: " + ip);
            return; // Or handle as "remove" or "don't change"
        }

        Instant newExpiry = Instant.now().plus(duration);

        // Atomically update the map.
        // The lambda expression is executed under a lock for the specific key.
        ipBlackMap.compute(ip, (key, currentExpiry) -> {
            if (currentExpiry == null) { // IP not present
                LOGGER.finer("IP " + key + " not in memory blacklist. Adding with expiry " + newExpiry);
                return newExpiry;
            } else { // IP already present
                if (Instant.now().isAfter(currentExpiry)) { // Current entry has expired
                    LOGGER.finer("IP " + key + " found in memory blacklist but expired (" + currentExpiry + "). Updating with new expiry " + newExpiry);
                    return newExpiry; // Replace expired entry
                } else {
                    // Current entry has not expired. Go's logic seems to keep the old expiry.
                    LOGGER.finer("IP " + key + " found in memory blacklist and not expired (" + currentExpiry + "). Keeping existing expiry.");
                    return currentExpiry; 
                }
            }
        });
    }

    /**
     * Checks if an IP address is currently blacklisted in memory.
     * If an IP is found but its blacklist duration has expired, it is removed
     * from the blacklist and considered not blocked.
     *
     * @param ip The IP address to check.
     * @return True if the IP is currently blacklisted, false otherwise.
     */
    public static boolean isIpBlacklisted(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        Instant expiryTime = ipBlackMap.get(ip);

        if (expiryTime == null) {
            return false; // Not in blacklist
        }

        if (Instant.now().isAfter(expiryTime)) {
            // Expired. Try to remove it atomically.
            // Only remove if the value (expiryTime) hasn't changed since we got it.
            boolean removed = ipBlackMap.remove(ip, expiryTime);
            if (removed) {
                 LOGGER.finer("Removed expired IP " + ip + " from in-memory blacklist.");
            }
            return false; // Expired, so not blocked
        }

        return true; // Present and not expired
    }
    
    /**
     * Clears all entries from the in-memory blacklist.
     * Useful for testing or resetting state.
     */
    public static void clearBlacklist() {
        ipBlackMap.clear();
        LOGGER.info("In-memory IP blacklist cleared.");
    }

    /**
     * Gets the current size of the in-memory blacklist.
     * @return The number of entries in the blacklist.
     */
    public static int getBlacklistSize() {
        return ipBlackMap.size();
    }


    private InMemoryIpBlacklist() {
        // Private constructor for utility class
    }
}
