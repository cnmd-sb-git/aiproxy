package com.example.aiproxy.common.notify;

import java.time.Duration;

/**
 * Interface for sending notifications.
 */
public interface Notifier {

    /**
     * Sends a notification.
     *
     * @param level   The severity level of the notification.
     * @param title   The title of the notification.
     * @param message The main content of the notification.
     */
    void notify(NotificationLevel level, String title, String message);

    /**
     * Sends a notification with throttling.
     * If a notification with the same level and key has been sent within the expiration period,
     * this notification might be suppressed or delayed by the implementation.
     *
     * @param level      The severity level of the notification.
     * @param key        A unique key for this type of notification, used for throttling.
     * @param expiration The duration for which this notification key should be throttled.
     * @param title      The title of the notification.
     * @param message    The main content of the notification.
     */
    void notifyThrottle(NotificationLevel level, String key, Duration expiration, String title, String message);
}
