package com.example.aiproxy.common.ipblack;

import com.example.aiproxy.common.RedisHelper; // Assumes RedisHelper is available

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages IP blacklisting operations.
 * It provides a layered approach, trying Redis first if enabled,
 * and falling back to an in-memory blacklist.
 */
public class IpBlacklistManager {

    private static final Logger LOGGER = Logger.getLogger(IpBlacklistManager.class.getName());

    /**
     * Adds an IP address to the blacklist for a specified duration.
     * Tries Redis first if enabled; otherwise, or if Redis fails, uses an in-memory blacklist.
     *
     * @param ip       The IP address to blacklist.
     * @param duration The duration for which the IP should be blacklisted.
     *                 A duration of zero or negative might imply permanent or default blacklist duration
     *                 depending on the underlying store's implementation.
     */
    public static void setIpBlack(String ip, Duration duration) {
        if (ip == null || ip.isEmpty()) {
            LOGGER.warning("IP address cannot be null or empty for blacklisting.");
            return;
        }

        if (RedisHelper.isRedisEnabled()) {
            try {
                // Assuming RedisIpBlacklist.setIpBlack returns true on success
                if (RedisIpBlacklist.setIpBlack(ip, duration)) {
                    LOGGER.fine("Successfully blacklisted IP " + ip + " in Redis for duration " + duration);
                    return; // Successfully set in Redis
                }
                // If RedisIpBlacklist.setIpBlack returns false, it implies an issue, log it.
                LOGGER.warning("Failed to set IP " + ip + " black in Redis (method returned false or threw handled exception). Falling back to memory.");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to set IP " + ip + " black in Redis: " + e.getMessage() + ". Falling back to memory.", e);
            }
        }
        // Fallback to in-memory blacklist
        InMemoryIpBlacklist.setIpBlack(ip, duration);
        LOGGER.fine("Blacklisted IP " + ip + " in memory for duration " + duration);
    }

    /**
     * Checks if an IP address is currently blacklisted.
     * Tries Redis first if enabled; otherwise, or if Redis fails, checks the in-memory blacklist.
     *
     * @param ip The IP address to check.
     * @return True if the IP is blacklisted, false otherwise.
     */
    public static boolean isIpBlacklisted(String ip) {
        if (ip == null || ip.isEmpty()) {
            LOGGER.warning("IP address cannot be null or empty for blacklist check.");
            return false;
        }

        if (RedisHelper.isRedisEnabled()) {
            try {
                // The Redis check should be conclusive if no error occurs.
                // The original Go code implies if err == nil, the 'ok' value is authoritative.
                Boolean redisBlocked = RedisIpBlacklist.isIpBlacklisted(ip);
                if (redisBlocked != null) { // If null, it means an error occurred determining status from Redis
                    return redisBlocked;
                }
                // If redisBlocked is null, it indicates an issue, so we log and fall through.
                 LOGGER.warning("Failed to get IP " + ip + " blacklist status from Redis (method returned null). Falling back to memory.");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to get IP " + ip + " blacklist status from Redis: " + e.getMessage() + ". Falling back to memory.", e);
            }
        }
        // Fallback to in-memory blacklist
        return InMemoryIpBlacklist.isIpBlacklisted(ip);
    }

    private IpBlacklistManager() {
        // Private constructor for utility class
    }
}
