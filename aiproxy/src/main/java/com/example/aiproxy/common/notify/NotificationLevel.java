package com.example.aiproxy.common.notify;

/**
 * Represents the severity level of a notification.
 */
public enum NotificationLevel {
    INFO("info"),
    WARN("warn"),
    ERROR("error");

    private final String levelStr;

    NotificationLevel(String levelStr) {
        this.levelStr = levelStr;
    }

    @Override
    public String toString() {
        return levelStr;
    }

    public static NotificationLevel fromString(String text) {
        for (NotificationLevel b : NotificationLevel.values()) {
            if (b.levelStr.equalsIgnoreCase(text)) {
                return b;
            }
        }
        return INFO; // Default or throw exception
    }
}
