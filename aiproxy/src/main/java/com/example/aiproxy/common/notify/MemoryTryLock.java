package com.example.aiproxy.common.notify;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Provides a simple in-memory, time-based lock mechanism.
 * Similar to `trylock.MemLock` in the Go codebase.
 */
public class MemoryTryLock {
    private static final Logger LOGGER = Logger.getLogger(MemoryTryLock.class.getName());
    private static final ConcurrentHashMap<String, Instant> lockMap = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire a lock for a given key.
     * The lock is acquired if the key is not currently locked or if the existing lock has expired.
     * If acquired, the lock is set for the specified duration.
     *
     * @param key      The unique key for the lock.
     * @param duration The duration for which the lock should be held if acquired.
     * @return True if the lock was acquired, false if it's currently held and not expired.
     */
    public static boolean tryLock(String key, Duration duration) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty.");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            // If duration is non-positive, lock effectively isn't set or expires immediately.
            // To prevent map pollution with instantly-expiring locks, we can treat it as lock not acquired
            // or simply remove the key if it exists. For simplicity, let's say it's not acquired.
            LOGGER.finer("TryLock called with non-positive duration for key '" + key + "'. Lock not acquired.");
            return false; 
        }

        Instant now = Instant.now();
        Instant newExpiry = now.plus(duration);

        // Atomically check and set the lock
        Instant previousExpiry = lockMap.compute(key, (k, currentExpiry) -> {
            if (currentExpiry == null || now.isAfter(currentExpiry)) { // Not locked or lock expired
                return newExpiry; // Acquire lock by setting new expiry
            }
            return currentExpiry; // Still locked, return existing expiry (don't change)
        });
        
        // If previousExpiry is null, it means the key was not in the map, so newExpiry was put.
        // If previousExpiry is not null and previousExpiry.equals(newExpiry), it means currentExpiry was null or expired, and newExpiry was set.
        // If previousExpiry is not null and !previousExpiry.equals(newExpiry), it means currentExpiry was valid and was returned by compute, so lock was not acquired.
        boolean acquired = previousExpiry == null || previousExpiry.equals(newExpiry) || now.isAfter(previousExpiry);


        if (acquired) {
            LOGGER.finer("Lock acquired for key '" + key + "' until " + newExpiry);
        } else {
            LOGGER.finer("Lock for key '" + key + "' currently held until " + previousExpiry + ". Not re-acquired.");
        }
        
        // Cleanup old keys (optional, can be done periodically if map grows too large)
        // For now, relying on natural expiration checks.

        return acquired;
    }
    
    /**
     * Clears an specific lock.
     * @param key The lock key to clear.
     */
    public static void clearLock(String key) {
        if (key != null) {
            lockMap.remove(key);
        }
    }

    /**
     * Clears all locks. Useful for testing or reset.
     */
    public static void clearAllLocks() {
        lockMap.clear();
    }


    private MemoryTryLock() {}
}
