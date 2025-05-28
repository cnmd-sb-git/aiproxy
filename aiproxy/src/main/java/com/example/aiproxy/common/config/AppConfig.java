package com.example.aiproxy.common.config;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class AppConfig {

    // Debugging flags
    public static final boolean DEBUG_ENABLED = EnvHelper.getBoolean("DEBUG", false);
    public static final boolean DEBUG_SQL_ENABLED = EnvHelper.getBoolean("DEBUG_SQL", false);

    // Database and General Settings
    public static final boolean DISABLE_AUTO_MIGRATE_DB = EnvHelper.getBoolean("DISABLE_AUTO_MIGRATE_DB", false);
    public static final String ADMIN_KEY = EnvHelper.getString("ADMIN_KEY", ""); // Default to empty string if not set
    public static final String WEB_PATH = EnvHelper.getString("WEB_PATH", "");    // Default to empty string
    public static final boolean DISABLE_WEB = EnvHelper.getBoolean("DISABLE_WEB", false);
    public static final boolean FFMPEG_ENABLED = EnvHelper.getBoolean("FFMPEG_ENABLED", false);


    // Atomic configurations - these can be changed at runtime via setters
    private static final AtomicBoolean disableServe = new AtomicBoolean(false);
    private static final AtomicLong logStorageHours = new AtomicLong(0); // 0 means no limit
    private static final AtomicLong retryLogStorageHours = new AtomicLong(0); // 0 means no limit
    private static final AtomicBoolean saveAllLogDetail = new AtomicBoolean(false);
    private static final AtomicLong logDetailRequestBodyMaxSize = new AtomicLong(128 * 1024); // 128KB
    private static final AtomicLong logDetailResponseBodyMaxSize = new AtomicLong(128 * 1024); // 128KB
    private static final AtomicLong logDetailStorageHours = new AtomicLong(3 * 24); // 3 days
    private static final AtomicLong cleanLogBatchSize = new AtomicLong(2000);
    private static final AtomicReference<String> internalToken = new AtomicReference<>("");
    private static final AtomicReference<String> notifyNote = new AtomicReference<>("");
    private static final AtomicLong ipGroupsThreshold = new AtomicLong(0);
    private static final AtomicLong ipGroupsBanThreshold = new AtomicLong(0);

    // Retry and Model Error Handling
    private static final AtomicLong retryTimes = new AtomicLong(0);
    private static final AtomicBoolean enableModelErrorAutoBan = new AtomicBoolean(false);
    private static final AtomicLong modelErrorAutoBanRate = new AtomicLong(Double.doubleToLongBits(0.3)); // Stored as long bits
    private static final AtomicReference<Map<Integer, Long>> timeoutWithModelType = new AtomicReference<>(Collections.emptyMap());
    public static final boolean DISABLE_MODEL_CONFIG = EnvHelper.getBoolean("DISABLE_MODEL_CONFIG", false);

    // Channel and Group Settings
    private static final AtomicReference<Map<Integer, List<String>>> defaultChannelModels = new AtomicReference<>(Collections.emptyMap());
    private static final AtomicReference<Map<Integer, Map<String, String>>> defaultChannelModelMapping = new AtomicReference<>(Collections.emptyMap());
    private static final AtomicLong groupMaxTokenNum = new AtomicLong(0); // 0 means unlimited
    private static final AtomicReference<Map<Double, Double>> groupConsumeLevelRatio = new AtomicReference<>(Collections.emptyMap());

    // Specific Model Settings (e.g., Gemini)
    private static final AtomicReference<String> geminiSafetySetting = new AtomicReference<>("BLOCK_NONE");

    // Billing
    private static final AtomicBoolean billingEnabled = new AtomicBoolean(true);

    // Static initializer block (like Go's init())
    static {
        // Initialize values that are read from env but also have setters that re-read from env
        // This ensures that the initial state considers environment variables.
        setRetryTimes(0); // Default 0, env var "RETRY_TIMES" can override
        setEnableModelErrorAutoBan(false); // Default false, env var "ENABLE_MODEL_ERROR_AUTO_BAN" can override
        setModelErrorAutoBanRate(0.3); // Default 0.3, env var "MODEL_ERROR_AUTO_BAN_RATE" can override
        setTimeoutWithModelType(new HashMap<>()); // Default empty, env var "TIMEOUT_WITH_MODEL_TYPE"
        
        setLogStorageHours(0);
        setRetryLogStorageHours(0);
        setLogDetailStorageHours(3 * 24);
        setCleanLogBatchSize(2000);
        setIPGroupsThreshold(0);
        setIPGroupsBanThreshold(0);
        setSaveAllLogDetail(false);
        setLogDetailRequestBodyMaxSize(128 * 1024);
        setLogDetailResponseBodyMaxSize(128 * 1024);
        setDisableServe(false);

        setDefaultChannelModels(new HashMap<>());
        setDefaultChannelModelMapping(new HashMap<>());
        setGroupConsumeLevelRatio(new HashMap<>());
        setGroupMaxTokenNum(0);
        
        setGeminiSafetySetting("BLOCK_NONE");
        setBillingEnabled(true);
        setInternalToken(EnvHelper.getString("INTERNAL_TOKEN", "")); // Explicitly use EnvHelper here for initial load
        setNotifyNote(EnvHelper.getString("NOTIFY_NOTE", ""));       // Explicitly use EnvHelper here
    }

    // --- Getters and Setters for atomic configurations ---

    public static boolean getDisableModelConfig() {
        return DISABLE_MODEL_CONFIG; // This is final, set from env at startup
    }

    public static long getRetryTimes() {
        return retryTimes.get();
    }

    public static void setRetryTimes(long defaultTimes) {
        retryTimes.set(EnvHelper.getLong("RETRY_TIMES", defaultTimes));
    }

    public static boolean getEnableModelErrorAutoBan() {
        return enableModelErrorAutoBan.get();
    }

    public static void setEnableModelErrorAutoBan(boolean defaultEnabled) {
        enableModelErrorAutoBan.set(EnvHelper.getBoolean("ENABLE_MODEL_ERROR_AUTO_BAN", defaultEnabled));
    }

    public static double getModelErrorAutoBanRate() {
        return Double.longBitsToDouble(modelErrorAutoBanRate.get());
    }

    public static void setModelErrorAutoBanRate(double defaultRate) {
        modelErrorAutoBanRate.set(Double.doubleToLongBits(EnvHelper.getDouble("MODEL_ERROR_AUTO_BAN_RATE", defaultRate)));
    }

    public static Map<Integer, Long> getTimeoutWithModelType() {
        return timeoutWithModelType.get();
    }

    public static void setTimeoutWithModelType(Map<Integer, Long> defaultTimeoutMap) {
        timeoutWithModelType.set(
            EnvHelper.getJson("TIMEOUT_WITH_MODEL_TYPE", defaultTimeoutMap, new TypeReference<Map<Integer, Long>>() {})
        );
    }

    public static long getLogStorageHours() {
        return logStorageHours.get();
    }

    public static void setLogStorageHours(long defaultHours) {
        logStorageHours.set(EnvHelper.getLong("LOG_STORAGE_HOURS", defaultHours));
    }

    public static long getRetryLogStorageHours() {
        return retryLogStorageHours.get();
    }

    public static void setRetryLogStorageHours(long defaultHours) {
        retryLogStorageHours.set(EnvHelper.getLong("RETRY_LOG_STORAGE_HOURS", defaultHours));
    }
    
    public static long getLogDetailStorageHours() {
        return logDetailStorageHours.get();
    }

    public static void setLogDetailStorageHours(long defaultHours) {
        logDetailStorageHours.set(EnvHelper.getLong("LOG_DETAIL_STORAGE_HOURS", defaultHours));
    }

    public static long getCleanLogBatchSize() {
        return cleanLogBatchSize.get();
    }

    public static void setCleanLogBatchSize(long defaultSize) {
        cleanLogBatchSize.set(EnvHelper.getLong("CLEAN_LOG_BATCH_SIZE", defaultSize));
    }
    
    public static long getIpGroupsThreshold() {
        return ipGroupsThreshold.get();
    }

    public static void setIPGroupsThreshold(long defaultThreshold) {
        ipGroupsThreshold.set(EnvHelper.getLong("IP_GROUPS_THRESHOLD", defaultThreshold));
    }

    public static long getIpGroupsBanThreshold() {
        return ipGroupsBanThreshold.get();
    }

    public static void setIPGroupsBanThreshold(long defaultThreshold) {
        ipGroupsBanThreshold.set(EnvHelper.getLong("IP_GROUPS_BAN_THRESHOLD", defaultThreshold));
    }

    public static boolean getSaveAllLogDetail() {
        return saveAllLogDetail.get();
    }

    public static void setSaveAllLogDetail(boolean defaultEnabled) {
        saveAllLogDetail.set(EnvHelper.getBoolean("SAVE_ALL_LOG_DETAIL", defaultEnabled));
    }

    public static long getLogDetailRequestBodyMaxSize() {
        return logDetailRequestBodyMaxSize.get();
    }

    public static void setLogDetailRequestBodyMaxSize(long defaultSize) {
        logDetailRequestBodyMaxSize.set(EnvHelper.getLong("LOG_DETAIL_REQUEST_BODY_MAX_SIZE", defaultSize));
    }
    
    public static long getLogDetailResponseBodyMaxSize() {
        return logDetailResponseBodyMaxSize.get();
    }

    public static void setLogDetailResponseBodyMaxSize(long defaultSize) {
        logDetailResponseBodyMaxSize.set(EnvHelper.getLong("LOG_DETAIL_RESPONSE_BODY_MAX_SIZE", defaultSize));
    }

    public static boolean getDisableServe() {
        return disableServe.get();
    }

    public static void setDisableServe(boolean defaultDisabled) {
        disableServe.set(EnvHelper.getBoolean("DISABLE_SERVE", defaultDisabled));
    }

    public static Map<Integer, List<String>> getDefaultChannelModels() {
        return defaultChannelModels.get();
    }

    public static void setDefaultChannelModels(Map<Integer, List<String>> defaultModels) {
        Map<Integer, List<String>> modelsFromEnv = EnvHelper.getJson(
            "DEFAULT_CHANNEL_MODELS", 
            defaultModels, 
            new TypeReference<Map<Integer, List<String>>>() {}
        );
        if (modelsFromEnv != null) {
            modelsFromEnv.forEach((key, ms) -> {
                if (ms != null) {
                    List<String> sortedAndCompacted = ms.stream()
                                                        .distinct()
                                                        .sorted()
                                                        .collect(Collectors.toList());
                    modelsFromEnv.put(key, sortedAndCompacted);
                }
            });
        }
        defaultChannelModels.set(modelsFromEnv);
    }

    public static Map<Integer, Map<String, String>> getDefaultChannelModelMapping() {
        return defaultChannelModelMapping.get();
    }

    public static void setDefaultChannelModelMapping(Map<Integer, Map<String, String>> defaultMapping) {
        defaultChannelModelMapping.set(
            EnvHelper.getJson("DEFAULT_CHANNEL_MODEL_MAPPING", defaultMapping, new TypeReference<Map<Integer, Map<String, String>>>() {})
        );
    }

    public static Map<Double, Double> getGroupConsumeLevelRatio() {
        return groupConsumeLevelRatio.get();
    }
    
    public static Map<String, Double> getGroupConsumeLevelRatioStringKeyMap() {
        Map<Double, Double> ratio = getGroupConsumeLevelRatio();
        if (ratio == null) return Collections.emptyMap();
        return ratio.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> String.valueOf(entry.getKey()), 
                Map.Entry::getValue
            ));
    }

    public static void setGroupConsumeLevelRatio(Map<Double, Double> defaultRatio) {
        groupConsumeLevelRatio.set(
            EnvHelper.getJson("GROUP_CONSUME_LEVEL_RATIO", defaultRatio, new TypeReference<Map<Double, Double>>() {})
        );
    }

    public static long getGroupMaxTokenNum() {
        return groupMaxTokenNum.get();
    }

    public static void setGroupMaxTokenNum(long defaultNum) {
        groupMaxTokenNum.set(EnvHelper.getLong("GROUP_MAX_TOKEN_NUM", defaultNum));
    }

    public static String getGeminiSafetySetting() {
        return geminiSafetySetting.get();
    }

    public static void setGeminiSafetySetting(String defaultSetting) {
        geminiSafetySetting.set(EnvHelper.getString("GEMINI_SAFETY_SETTING", defaultSetting));
    }

    public static boolean getBillingEnabled() {
        return billingEnabled.get();
    }

    public static void setBillingEnabled(boolean defaultEnabled) {
        billingEnabled.set(EnvHelper.getBoolean("BILLING_ENABLED", defaultEnabled));
    }
    
    public static String getInternalToken() {
        return internalToken.get();
    }

    public static void setInternalToken(String defaultToken) {
        // This setter follows the pattern of others: defaultToken is used if env var is not set.
        // The static initializer already calls this with EnvHelper.getString("INTERNAL_TOKEN", "").
        internalToken.set(EnvHelper.getString("INTERNAL_TOKEN", defaultToken));
    }

    public static String getNotifyNote() {
        return notifyNote.get();
    }

    public static void setNotifyNote(String defaultNote) {
        notifyNote.set(EnvHelper.getString("NOTIFY_NOTE", defaultNote));
    }

    private AppConfig() {
        // Private constructor to prevent instantiation
    }
}
