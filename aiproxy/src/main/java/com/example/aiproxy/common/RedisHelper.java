package com.example.aiproxy.common;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

// Assuming Jedis library is used for Redis operations.
// Add to pom.xml:
// <dependency>
//   <groupId>redis.clients</groupId>
//   <artifactId>jedis</artifactId>
//   <version>5.1.0</version> <!-- Use appropriate version -->
// </dependency>
public class RedisHelper {

    private static final Logger LOGGER = Logger.getLogger(RedisHelper.class.getName());
    private static JedisPool jedisPool;
    private static boolean redisEnabled = false;

    static {
        initRedisClient();
    }

    private static void initRedisClient() {
        String redisConnString = System.getenv("REDIS_CONN_STRING");
        if (redisConnString == null || redisConnString.isEmpty()) {
            LOGGER.info("REDIS_CONN_STRING not set, redis is not enabled");
            redisEnabled = false;
            return;
        }

        try {
            URI redisUri = new URI(redisConnString);
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            // Configure poolConfig if needed (e.g., max total, max idle)
            
            // Timeout for operations, similar to context.WithTimeout for ping
            // Jedis default connection timeout is 2000ms, socket timeout 2000ms
            // For ParseURL like behavior, need to handle user/pass if present in URI
            String userInfo = redisUri.getUserInfo();
            String password = null;
            if (userInfo != null && userInfo.contains(":")) {
                password = userInfo.substring(userInfo.indexOf(':') + 1);
            }

            jedisPool = new JedisPool(poolConfig, redisUri.getHost(), redisUri.getPort(), 2000, password);
            
            LOGGER.info("Redis is enabled. Connecting to: " + redisUri.getHost() + ":" + redisUri.getPort());

            try (Jedis jedis = jedisPool.getResource()) {
                String pingResult = jedis.ping();
                if ("PONG".equalsIgnoreCase(pingResult)) {
                    LOGGER.info("Successfully connected to Redis and received PONG.");
                    redisEnabled = true;
                } else {
                    LOGGER.warning("Failed to ping Redis. Received: " + pingResult);
                    redisEnabled = false;
                    jedisPool.close(); // Clean up the pool if ping fails
                    jedisPool = null;
                }
            } catch (JedisException e) {
                LOGGER.log(Level.SEVERE, "Failed to connect or ping Redis: " + e.getMessage(), e);
                redisEnabled = false;
                if (jedisPool != null) {
                    jedisPool.close();
                    jedisPool = null;
                }
            }
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Failed to parse REDIS_CONN_STRING: " + e.getMessage(), e);
            redisEnabled = false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An unexpected error occurred during Redis initialization: " + e.getMessage(), e);
            redisEnabled = false;
            if (jedisPool != null) {
                 jedisPool.close();
                 jedisPool = null;
            }
        }
    }

    public static boolean isRedisEnabled() {
        return redisEnabled;
    }

    /**
     * Retrieves a Jedis resource from the pool.
     * The caller is responsible for closing the Jedis instance (e.g., using try-with-resources).
     *
     * @return A Jedis instance, or null if Redis is not enabled or the pool is not initialized.
     * @throws JedisException if the pool is exhausted or other Redis errors occur.
     */
    public static Jedis getJedisResource() {
        if (!redisEnabled || jedisPool == null) {
            LOGGER.warning("Redis is not enabled or pool not initialized. Cannot get Jedis resource.");
            return null;
        }
        return jedisPool.getResource();
    }
    
    public static String redisGet(String key) {
        if (!redisEnabled || jedisPool == null) {
            LOGGER.warning("Redis is not enabled, cannot GET key: " + key);
            return null; // Or throw exception
        }
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (JedisException e) {
            LOGGER.log(Level.SEVERE, "Error getting key from Redis: " + key, e);
            return null; // Or throw
        }
    }

    public static boolean redisSet(String key, String value, Duration expiration) {
        if (!redisEnabled || jedisPool == null) {
            LOGGER.warning("Redis is not enabled, cannot SET key: " + key);
            return false;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            String result;
            if (expiration != null && !expiration.isZero() && !expiration.isNegative()) {
                result = jedis.setex(key, expiration.getSeconds(), value);
            } else {
                result = jedis.set(key, value);
            }
            return "OK".equalsIgnoreCase(result);
        } catch (JedisException e) {
            LOGGER.log(Level.SEVERE, "Error setting key in Redis: " + key, e);
            return false;
        }
    }
    
    public static Long redisDel(String key) {
        if (!redisEnabled || jedisPool == null) {
            LOGGER.warning("Redis is not enabled, cannot DEL key: " + key);
            return null; // Or throw exception
        }
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.del(key);
        } catch (JedisException e) {
            LOGGER.log(Level.SEVERE, "Error deleting key from Redis: " + key, e);
            return null; // Or throw
        }
    }

    // Call this method on application shutdown to clean up the pool
    public static void close() {
        if (jedisPool != null) {
            LOGGER.info("Closing Redis connection pool.");
            jedisPool.close();
            redisEnabled = false;
        }
    }
    
    // Private constructor to prevent instantiation
    private RedisHelper() {
    }
}
