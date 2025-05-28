package com.example.aiproxy.common;

import java.util.concurrent.atomic.AtomicBoolean;

public class ColorHelper {

    private static boolean needColor;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    public static boolean needColor() {
        if (initialized.compareAndSet(false, true)) {
            // System.console() is null if not running in a terminal
            // This is a basic check and might not cover all environments like go-isatty does.
            // For example, it might not detect Cygwin terminals correctly.
            // For a more robust solution, a library like Jansi (if already a dependency or if added)
            // or platform-specific checks would be needed.
            needColor = (System.console() != null);
        }
        return needColor;
    }

    // Private constructor to prevent instantiation
    private ColorHelper() {
    }
}
