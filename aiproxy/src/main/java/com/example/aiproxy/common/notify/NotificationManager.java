package com.example.aiproxy.common.notify;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Manages and provides access to notification services.
 * It holds a default Notifier instance and provides static methods
 * for sending notifications.
 */
public class NotificationManager {
    private static final Logger LOGGER = Logger.getLogger(NotificationManager.class.getName());

    // Initialize with a standard output notifier by default.
    // This will be created when StdNotifier.java is translated.
    private static volatile Notifier defaultNotifier = new StdNotifier(); // Placeholder until StdNotifier is defined

    /**
     * Sets the default notifier implementation to be used by the static helper methods.
     *
     * @param notifier The Notifier implementation to set as default. Must not be null.
     */
    public static void setDefaultNotifier(Notifier notifier) {
        if (notifier == null) {
            throw new IllegalArgumentException("Default notifier cannot be null.");
        }
        LOGGER.info("Setting default notifier to: " + notifier.getClass().getName());
        defaultNotifier = notifier;
    }

    /**
     * Gets the currently configured default Notifier.
     * @return The default Notifier instance.
     */
    public static Notifier getDefaultNotifier() {
        return defaultNotifier;
    }
    
    /**
     * Sends a notification using the default notifier.
     *
     * @param level   The severity level.
     * @param title   The title of the notification.
     * @param message The message content.
     */
    public static void notify(NotificationLevel level, String title, String message) {
        defaultNotifier.notify(level, title, message);
    }

    public static void info(String title, String message) {
        defaultNotifier.notify(NotificationLevel.INFO, title, message);
    }

    public static void warn(String title, String message) {
        defaultNotifier.notify(NotificationLevel.WARN, title, message);
    }

    public static void error(String title, String message) {
        defaultNotifier.notify(NotificationLevel.ERROR, title, message);
    }

    private static String formatThrottleKey(NotificationLevel level, String key) {
        return String.format("notifylimit:%s:%s", level.toString().toLowerCase(), key);
    }

    /**
     * Sends a throttled notification using the default notifier.
     *
     * @param level      The severity level.
     * @param key        A unique key for throttling this specific notification type.
     * @param expiration The duration for which this notification should be throttled.
     * @param title      The title of the notification.
     * @param message    The message content.
     */
    public static void throttle(NotificationLevel level, String key, Duration expiration, String title, String message) {
        defaultNotifier.notifyThrottle(level, formatThrottleKey(level, key), expiration, title, message);
    }

    public static void infoThrottle(String key, Duration expiration, String title, String message) {
        throttle(NotificationLevel.INFO, key, expiration, title, message);
    }

    public static void warnThrottle(String key, Duration expiration, String title, String message) {
        throttle(NotificationLevel.WARN, key, expiration, title, message);
    }

    public static void errorThrottle(String key, Duration expiration, String title, String message) {
        throttle(NotificationLevel.ERROR, key, expiration, title, message);
    }

    private NotificationManager() {
        // Private constructor for utility class
    }
}
