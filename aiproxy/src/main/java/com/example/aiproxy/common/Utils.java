package com.example.aiproxy.common;

import java.util.UUID;

public class Utils {

    /**
     * Generates a short UUID string.
     * This is equivalent to generating a standard UUID and then representing it as a
     * 32-character hexadecimal string (without hyphens).
     *
     * @return A 32-character hexadecimal string representing a UUID.
     */
    public static String shortUUID() {
        // java.util.UUID.randomUUID().toString() produces a string like:
        // "xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx" (36 characters with hyphens)
        // We want the 32-character hex string.
        return UUID.randomUUID().toString().replace("-", "");
    }

    // Private constructor to prevent instantiation
    private Utils() {
    }
}
