package cn.mono.aiproxy.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Getter
@Setter
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Default values inspired by core/model/option.go and core/common/config/default.go
    private Long logStorageHours = 720L; // 30 days
    private Long retryLogStorageHours = 720L; // 30 days
    private Long logDetailStorageHours = 7L * 24L; // 7 days
    private Long cleanLogBatchSize = 100L;
    private Long ipGroupsThreshold = 1000L;
    private Long ipGroupsBanThreshold = 100L;
    private Boolean saveAllLogDetail = false;
    private Long logDetailRequestBodyMaxSize = 1024L * 10L; // 10KB
    private Long logDetailResponseBodyMaxSize = 1024L * 10L; // 10KB
    private Boolean disableServe = false;
    private Boolean billingEnabled = false;
    private Long retryTimes = 3L;
    private Double modelErrorAutoBanRate = 0.2;
    private Boolean enableModelErrorAutoBan = true;
    private String timeoutWithModelType = "{}"; // Default empty JSON map
    private String defaultChannelModels = "{}"; // Default empty JSON map
    private String defaultChannelModelMapping = "{}"; // Default empty JSON map
    private String geminiSafetySetting = "BLOCK_NONE";
    private Long groupMaxTokenNum = 1000000L;
    private String groupConsumeLevelRatio = "{\"1\":1.0,\"2\":0.9,\"3\":0.8,\"4\":0.7,\"5\":0.6}"; // Default ratios
    private String internalToken = "";
    private String notifyNote = "";
    private Long defaultApiTimeoutSeconds = 60L; // Added

    // Getters that deserialize JSON strings on demand (example)
    public Map<Integer, Long> getTimeoutWithModelTypeMap() {
        try {
            return objectMapper.readValue(timeoutWithModelType, new TypeReference<Map<Integer, Long>>() {});
        } catch (IOException e) {
            logger.error("Failed to parse timeoutWithModelType JSON: {}", timeoutWithModelType, e);
            return Collections.emptyMap();
        }
    }

    public Map<Integer, List<String>> getDefaultChannelModelsMap() {
        try {
            return objectMapper.readValue(defaultChannelModels, new TypeReference<Map<Integer, List<String>>>() {});
        } catch (IOException e) {
            logger.error("Failed to parse defaultChannelModels JSON: {}", defaultChannelModels, e);
            return Collections.emptyMap();
        }
    }
    
    public Map<Integer, Map<String, String>> getDefaultChannelModelMappingMap() {
        try {
            return objectMapper.readValue(defaultChannelModelMapping, new TypeReference<Map<Integer, Map<String, String>>>() {});
        } catch (IOException e) {
            logger.error("Failed to parse defaultChannelModelMapping JSON: {}", defaultChannelModelMapping, e);
            return Collections.emptyMap();
        }
    }

    public Map<String, Double> getGroupConsumeLevelRatioMap() {
        try {
            return objectMapper.readValue(groupConsumeLevelRatio, new TypeReference<Map<String, Double>>() {});
        } catch (IOException e) {
            logger.error("Failed to parse groupConsumeLevelRatio JSON: {}", groupConsumeLevelRatio, e);
            return Collections.emptyMap();
        }
    }
}
