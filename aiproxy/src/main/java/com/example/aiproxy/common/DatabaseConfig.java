package com.example.aiproxy.common;

public class DatabaseConfig {

    // Flags to indicate the type of database being used.
    // These would typically be set based on application configuration.
    public static boolean USING_SQLITE = false;
    public static boolean USING_POSTGRESQL = false;
    public static boolean USING_MYSQL = false;

    // Configuration for SQLite
    // In a real application, these would likely be read from a configuration file
    // or environment variables. For now, defaults are used.
    public static final String SQLITE_PATH = System.getenv().getOrDefault("SQLITE_PATH", "aiproxy.db");
    public static final long SQLITE_BUSY_TIMEOUT = Long.parseLong(System.getenv().getOrDefault("SQLITE_BUSY_TIMEOUT", "3000"));

    // Private constructor to prevent instantiation
    private DatabaseConfig() {
    }

    // Example method to set the database type, could be called during application startup
    public static void setDatabaseType(String type) {
        USING_SQLITE = "sqlite".equalsIgnoreCase(type);
        USING_POSTGRESQL = "postgresql".equalsIgnoreCase(type);
        USING_MYSQL = "mysql".equalsIgnoreCase(type);

        // Potentially, validate that only one is true or handle errors.
    }
}
