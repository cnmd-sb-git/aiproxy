package com.example.aiproxy.common.notify;

import com.example.aiproxy.common.config.AppConfig; // For AppConfig.getNotifyNote()

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Notifier implementation that logs notifications to standard output
 * using {@link java.util.logging.Logger}.
 * This is analogous to Go's StdNotifier which used Logrus.
 */
public class StdNotifier implements Notifier {

    // Using java.util.logging.Logger. The Go version used Logrus with fields.
    // We can simulate fields by formatting the log message.
    private static final Logger LOGGER = Logger.getLogger(StdNotifier.class.getName());

    @Override
    public void notify(NotificationLevel level, String title, String message) {
        String notifyNote = AppConfig.getNotifyNote(); // Get the global note from AppConfig

        // Constructing a log message that includes title and the global note if present.
        StringBuilder logMessage = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            logMessage.append("[").append(title).append("] ");
        }
        logMessage.append(message);
        if (notifyNote != null && !notifyNote.isEmpty()) {
            logMessage.append(" (Note: ").append(notifyNote).append(")");
        }

        switch (level) {
            case INFO:
                LOGGER.log(Level.INFO, logMessage.toString());
                break;
            case WARN:
                LOGGER.log(Level.WARNING, logMessage.toString());
                break;
            case ERROR:
                LOGGER.log(Level.SEVERE, logMessage.toString());
                break;
            default:
                LOGGER.log(Level.INFO, "[UNKNOWN_LEVEL] " + logMessage.toString());
                break;
        }
    }

    @Override
    public void notifyThrottle(NotificationLevel level, String key, Duration expiration, String title, String message) {
        // The key passed here is already formatted by NotificationManager.formatThrottleKey
        if (!MemoryTryLock.tryLock(key, expiration)) {
            LOGGER.finer("Notification throttled for key: " + key + ", title: " + title);
            return;
        }
        // If lock is acquired, proceed to notify
        notify(level, title, message);
    }
}
