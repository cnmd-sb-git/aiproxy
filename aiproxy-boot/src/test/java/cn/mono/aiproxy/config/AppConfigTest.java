package cn.mono.aiproxy.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class AppConfigTest {

    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig();
    }

    @Test
    void testDefaultValues() {
        assertThat(appConfig.getLogStorageHours()).isEqualTo(720L);
        assertThat(appConfig.getRetryTimes()).isEqualTo(3L);
        assertThat(appConfig.getBillingEnabled()).isFalse();
        assertThat(appConfig.getDefaultApiTimeoutSeconds()).isEqualTo(60L);
        assertThat(appConfig.getTimeoutWithModelType()).isEqualTo("{}"); // Default empty JSON map
        assertThat(appConfig.getGroupConsumeLevelRatio()).isEqualTo("{\"1\":1.0,\"2\":0.9,\"3\":0.8,\"4\":0.7,\"5\":0.6}");
    }

    @Test
    void testSettersAndGetters() {
        appConfig.setLogStorageHours(100L);
        assertThat(appConfig.getLogStorageHours()).isEqualTo(100L);

        appConfig.setBillingEnabled(true);
        assertThat(appConfig.getBillingEnabled()).isTrue();

        appConfig.setDefaultApiTimeoutSeconds(120L);
        assertThat(appConfig.getDefaultApiTimeoutSeconds()).isEqualTo(120L);

        String newTimeoutJson = "{\"1\": 30, \"2\": 60}";
        appConfig.setTimeoutWithModelType(newTimeoutJson);
        assertThat(appConfig.getTimeoutWithModelType()).isEqualTo(newTimeoutJson);
    }

    @Test
    void testGetTimeoutWithModelTypeMap_validJson() {
        appConfig.setTimeoutWithModelType("{\"1\": 30000, \"2\": 60000}");
        Map<Integer, Long> timeoutMap = appConfig.getTimeoutWithModelTypeMap();
        assertThat(timeoutMap).containsExactly(entry(1, 30000L), entry(2, 60000L));
    }

    @Test
    void testGetTimeoutWithModelTypeMap_emptyJson() {
        appConfig.setTimeoutWithModelType("{}");
        Map<Integer, Long> timeoutMap = appConfig.getTimeoutWithModelTypeMap();
        assertThat(timeoutMap).isEmpty();
    }

    @Test
    void testGetTimeoutWithModelTypeMap_invalidJson() {
        appConfig.setTimeoutWithModelType("invalid-json");
        Map<Integer, Long> timeoutMap = appConfig.getTimeoutWithModelTypeMap();
        assertThat(timeoutMap).isEmpty(); // Should return empty map and log error
    }
    
    @Test
    void testGetDefaultChannelModelsMap_validJson() {
        appConfig.setDefaultChannelModels("{\"1\": [\"model-a\", \"model-b\"], \"2\": [\"model-c\"]}");
        Map<Integer, List<String>> channelModelsMap = appConfig.getDefaultChannelModelsMap();
        assertThat(channelModelsMap)
            .hasSize(2)
            .containsEntry(1, List.of("model-a", "model-b"))
            .containsEntry(2, List.of("model-c"));
    }

    @Test
    void testGetDefaultChannelModelsMap_emptyJson() {
        appConfig.setDefaultChannelModels("{}");
        Map<Integer, List<String>> channelModelsMap = appConfig.getDefaultChannelModelsMap();
        assertThat(channelModelsMap).isEmpty();
    }
    
    @Test
    void testGetDefaultChannelModelMappingMap_validJson() {
        appConfig.setDefaultChannelModelMapping("{\"1\": {\"source1\":\"target1\"}, \"2\": {\"source2\":\"target2\"}}");
        Map<Integer, Map<String,String>> mapping = appConfig.getDefaultChannelModelMappingMap();
        assertThat(mapping).hasSize(2);
        assertThat(mapping.get(1)).containsExactly(entry("source1", "target1"));
        assertThat(mapping.get(2)).containsExactly(entry("source2", "target2"));
    }

    @Test
    void testGetGroupConsumeLevelRatioMap_validJson() {
        appConfig.setGroupConsumeLevelRatio("{\"1\":1.0,\"2\":0.9}");
        Map<String, Double> ratioMap = appConfig.getGroupConsumeLevelRatioMap();
        assertThat(ratioMap).containsExactly(entry("1", 1.0), entry("2", 0.9));
    }

    @Test
    void testGetGroupConsumeLevelRatioMap_invalidJson() {
        appConfig.setGroupConsumeLevelRatio("invalid-json");
        Map<String, Double> ratioMap = appConfig.getGroupConsumeLevelRatioMap();
        assertThat(ratioMap).isEmpty();
    }
}
