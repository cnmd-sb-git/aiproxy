package com.example.aiproxy.common.ipblack;

import com.example.aiproxy.common.RedisHelper; // Assuming RedisHelper is available
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis-backed implementation for IP blacklisting.
 * This class provides static methods to manage an IP blacklist using Redis.
 */
public class RedisIpBlacklist {

    private static final Logger LOGGER = Logger.getLogger(RedisIpBlacklist.class.getName());
    private static final String IP_BLACK_KEY_PREFIX = "ip_black:"; // Matches "ip_black:%s"

    /**
     * Adds an IP address to the Redis-backed blacklist for a specified duration,
     * only if the IP is not already blacklisted.
     *
     * @param ip       The IP address to blacklist.
     * @param duration The duration for which the IP should be blacklisted.
     *                 If non-positive, the behavior might depend on Redis version/config for EX,
     *                 but typically means it won't be set or will expire immediately.
     *                 It's advisable to use positive durations.
     * @return True if the IP was successfully blacklisted (i.e., it was newly added).
     *         False if the IP was already blacklisted or an error occurred.
     * @throws JedisException if a Redis connectivity error occurs.
     */
    public static boolean setIpBlack(String ip, Duration duration) throws JedisException {
        if (ip == null || ip.isEmpty()) {
            LOGGER.warning("IP address for Redis blacklist cannot be null or empty.");
            return false;
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            LOGGER.warning("Invalid duration for Redis blacklist for IP " + ip + ": " + duration + ". IP will not be blacklisted or will expire immediately.");
            // SetNX with non-positive expiry might not work as intended or might vary by Redis version.
            // To effectively not blacklist, we can return false.
            return false; 
        }

        String key = IP_BLACK_KEY_PREFIX + ip;
        long seconds = duration.getSeconds();
        if (seconds <= 0) { // Should be caught by above, but as a safeguard for ex argument.
             LOGGER.warning("Duration in seconds is non-positive for IP " + ip + ". IP will not be blacklisted effectively.");
             return false;
        }

        try (Jedis jedis = RedisHelper.getJedisResource()) {
            if (jedis == null) {
                LOGGER.severe("Could not get Jedis resource. Redis operations unavailable.");
                return false; // Indicate failure to set in Redis
            }
            // Using "1" as a placeholder value, common for existence checks.
            // SetParams().nx() ensures it's only set if the key does not exist.
            // SetParams().ex() sets the expiration time in seconds.
            String result = jedis.set(key, "1", SetParams.setParams().nx().ex((int) seconds));
            
            // "OK" is returned by Jedis/Redis if SETNX was successful (key was set).
            // Null is returned if the key already existed (NX condition not met).
            boolean success = "OK".equalsIgnoreCase(result);
            if (success) {
                 LOGGER.finer("IP " + ip + " successfully blacklisted in Redis for " + seconds + " seconds.");
            } else {
                 LOGGER.finer("IP " + ip + " was already blacklisted in Redis or SETNX failed for other reasons (result: " + result + ").");
            }
            return success; 
        } catch (JedisException e) {
            LOGGER.log(Level.SEVERE, "JedisException while setting IP " + ip + " in Redis blacklist: " + e.getMessage(), e);
            throw e; // Re-throw to be caught by IpBlacklistManager for fallback consideration
        }
    }

    /**
     * Checks if an IP address is currently blacklisted in Redis.
     *
     * @param ip The IP address to check.
     * @return True if the IP exists in the Redis blacklist (and hasn't expired),
     *         False if not found or an error occurs (after logging).
     *         Returns null if a significant error occurs that prevents determination (e.g. unable to get Jedis resource)
     * @throws JedisException if a Redis connectivity error occurs.
     */
    public static Boolean isIpBlacklisted(String ip) throws JedisException {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String key = IP_BLACK_KEY_PREFIX + ip;
        try (Jedis jedis = RedisHelper.getJedisResource()) {
            if (jedis == null) {
                LOGGER.severe("Could not get Jedis resource for IP blacklist check. Returning null (status undetermined).");
                return null; // Status undetermined
            }
            Boolean exists = jedis.exists(key);
            LOGGER.finer("IP " + ip + (exists ? " " : " not ") + "found in Redis blacklist.");
            return exists;
        } catch (JedisException e) {
            LOGGER.log(Level.SEVERE, "JedisException while checking IP " + ip + " in Redis blacklist: " + e.getMessage(), e);
            throw e; // Re-throw for IpBlacklistManager to handle
        }
    }

    private RedisIpBlacklist() {
        // Private constructor for utility class
    }
}
