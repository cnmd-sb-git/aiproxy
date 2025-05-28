package cn.mono.aiproxy.service;

import cn.mono.aiproxy.config.AppConfig;
import cn.mono.aiproxy.model.OptionEntity;
import cn.mono.aiproxy.repository.OptionRepository;
import cn.mono.aiproxy.service.dto.OptionDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OptionService {

    private static final Logger logger = LoggerFactory.getLogger(OptionService.class);
    private final OptionRepository optionRepository;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper = new ObjectMapper(); // For JSON validation

    // Simplified provider just to get the default value string from AppConfig
    private record OptionDefaultProvider(Function<AppConfig, String> defaultValueGetter) {}

    private static final Map<String, OptionDefaultProvider> OPTION_DEFAULTS = Map.ofEntries(
        Map.entry("LogStorageHours", new OptionDefaultProvider(ac -> ac.getLogStorageHours().toString())),
        Map.entry("RetryLogStorageHours", new OptionDefaultProvider(ac -> ac.getRetryLogStorageHours().toString())),
        Map.entry("LogDetailStorageHours", new OptionDefaultProvider(ac -> ac.getLogDetailStorageHours().toString())),
        Map.entry("CleanLogBatchSize", new OptionDefaultProvider(ac -> ac.getCleanLogBatchSize().toString())),
        Map.entry("IpGroupsThreshold", new OptionDefaultProvider(ac -> ac.getIpGroupsThreshold().toString())),
        Map.entry("IpGroupsBanThreshold", new OptionDefaultProvider(ac -> ac.getIpGroupsBanThreshold().toString())),
        Map.entry("SaveAllLogDetail", new OptionDefaultProvider(ac -> ac.getSaveAllLogDetail().toString())),
        Map.entry("LogDetailRequestBodyMaxSize", new OptionDefaultProvider(ac -> ac.getLogDetailRequestBodyMaxSize().toString())),
        Map.entry("LogDetailResponseBodyMaxSize", new OptionDefaultProvider(ac -> ac.getLogDetailResponseBodyMaxSize().toString())),
        Map.entry("DisableServe", new OptionDefaultProvider(ac -> ac.getDisableServe().toString())),
        Map.entry("BillingEnabled", new OptionDefaultProvider(ac -> ac.getBillingEnabled().toString())),
        Map.entry("RetryTimes", new OptionDefaultProvider(ac -> ac.getRetryTimes().toString())),
        Map.entry("ModelErrorAutoBanRate", new OptionDefaultProvider(ac -> ac.getModelErrorAutoBanRate().toString())),
        Map.entry("EnableModelErrorAutoBan", new OptionDefaultProvider(ac -> ac.getEnableModelErrorAutoBan().toString())),
        Map.entry("TimeoutWithModelType", new OptionDefaultProvider(AppConfig::getTimeoutWithModelType)),
        Map.entry("DefaultChannelModels", new OptionDefaultProvider(AppConfig::getDefaultChannelModels)),
        Map.entry("DefaultChannelModelMapping", new OptionDefaultProvider(AppConfig::getDefaultChannelModelMapping)),
        Map.entry("GeminiSafetySetting", new OptionDefaultProvider(AppConfig::getGeminiSafetySetting)),
        Map.entry("GroupMaxTokenNum", new OptionDefaultProvider(ac -> ac.getGroupMaxTokenNum().toString())),
        Map.entry("GroupConsumeLevelRatio", new OptionDefaultProvider(AppConfig::getGroupConsumeLevelRatio)),
        Map.entry("InternalToken", new OptionDefaultProvider(AppConfig::getInternalToken)),
        Map.entry("NotifyNote", new OptionDefaultProvider(AppConfig::getNotifyNote))
    );
    
    private void validateJson(String jsonString, TypeReference<?> typeReference, String key) {
        try {
            objectMapper.readValue(jsonString, typeReference);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON for " + key + ": " + jsonString, e);
        }
    }

    public OptionDTO convertToDTO(OptionEntity optionEntity) {
        if (optionEntity == null) return null;
        OptionDTO dto = new OptionDTO();
        BeanUtils.copyProperties(optionEntity, dto);
        return dto;
    }

    public OptionEntity convertToEntity(OptionDTO optionDTO) {
        if (optionDTO == null) return null;
        OptionEntity entity = new OptionEntity();
        BeanUtils.copyProperties(optionDTO, entity);
        return entity;
    }

    @Transactional(readOnly = true)
    public List<OptionDTO> getAllOptions() {
        return optionRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<OptionDTO> getOptionByKey(String key) {
        return optionRepository.findById(key).map(this::convertToDTO);
    }
    
    @Transactional(readOnly = true)
    public List<OptionDTO> getOptionsByKeys(List<String> keys) {
        return optionRepository.findByKeyIn(keys).stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public OptionDTO updateOption(OptionDTO optionDTO) {
        if (optionDTO == null || optionDTO.getKey() == null) {
            throw new IllegalArgumentException("OptionDTO and its key must not be null");
        }
        OptionEntity optionEntity = convertToEntity(optionDTO);
        optionEntity = optionRepository.save(optionEntity);
        // Update AppConfig after successful save
        updateAppConfig(optionEntity.getKey(), optionEntity.getValue());
        return convertToDTO(optionEntity);
    }

    @Transactional
    public List<OptionDTO> updateOptions(Map<String, String> optionsToUpdate) {
        List<OptionEntity> updatedEntities = optionsToUpdate.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    OptionEntity entity = optionRepository.findById(key)
                            .orElse(new OptionEntity(key, null)); // Create if not exists
                    entity.setValue(value);
                    OptionEntity savedEntity = optionRepository.save(entity);
                    // Update AppConfig after successful save
                    updateAppConfig(savedEntity.getKey(), savedEntity.getValue());
                    return savedEntity;
                })
                .collect(Collectors.toList());
        return updatedEntities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }
    
    private void updateAppConfig(String key, String value) {
        if (value == null) {
            logger.warn("Received null value for key '{}'. Attempting to use default from AppConfig.", key);
            OptionDefaultProvider provider = OPTION_DEFAULTS.get(key);
            if (provider != null) {
                value = provider.defaultValueGetter.apply(appConfig); // Get current default from AppConfig
                logger.info("Using default value for key '{}': {}", key, value);
                if (value == null && !key.equals("InternalToken") && !key.equals("NotifyNote")) { // Allow some strings to be empty
                     logger.error("Default value for key '{}' is also null. Critical configuration missing.", key);
                     // Depending on the key, might throw an error or use a hardcoded failsafe default.
                     // For now, just log and return if the default is also null.
                     return;
                }
            } else {
                logger.error("No default provider for key '{}' and value is null. Cannot update AppConfig.", key);
                return;
            }
        }

        try {
            switch (key) {
                case "LogStorageHours": appConfig.setLogStorageHours(Long.parseLong(value)); break;
                case "RetryLogStorageHours": appConfig.setRetryLogStorageHours(Long.parseLong(value)); break;
                case "LogDetailStorageHours": appConfig.setLogDetailStorageHours(Long.parseLong(value)); break;
                case "CleanLogBatchSize": appConfig.setCleanLogBatchSize(Long.parseLong(value)); break;
                case "IpGroupsThreshold": appConfig.setIpGroupsThreshold(Long.parseLong(value)); break;
                case "IpGroupsBanThreshold": appConfig.setIpGroupsBanThreshold(Long.parseLong(value)); break;
                case "SaveAllLogDetail": appConfig.setSaveAllLogDetail(Boolean.parseBoolean(value)); break;
                case "LogDetailRequestBodyMaxSize": appConfig.setLogDetailRequestBodyMaxSize(Long.parseLong(value)); break;
                case "LogDetailResponseBodyMaxSize": appConfig.setLogDetailResponseBodyMaxSize(Long.parseLong(value)); break;
                case "DisableServe": appConfig.setDisableServe(Boolean.parseBoolean(value)); break;
                case "BillingEnabled": appConfig.setBillingEnabled(Boolean.parseBoolean(value)); break;
                case "RetryTimes": appConfig.setRetryTimes(Long.parseLong(value)); break;
                case "ModelErrorAutoBanRate": appConfig.setModelErrorAutoBanRate(Double.parseDouble(value)); break;
                case "EnableModelErrorAutoBan": appConfig.setEnableModelErrorAutoBan(Boolean.parseBoolean(value)); break;
                case "GeminiSafetySetting": appConfig.setGeminiSafetySetting(value); break;
                case "GroupMaxTokenNum": appConfig.setGroupMaxTokenNum(Long.parseLong(value)); break;
                case "InternalToken": appConfig.setInternalToken(value); break;
                case "NotifyNote": appConfig.setNotifyNote(value); break;
                
                case "TimeoutWithModelType":
                    validateJson(value, new TypeReference<Map<Integer, Long>>() {}, key);
                    appConfig.setTimeoutWithModelType(value);
                    break;
                case "DefaultChannelModels":
                    validateJson(value, new TypeReference<Map<Integer, List<String>>>() {}, key);
                    appConfig.setDefaultChannelModels(value);
                    break;
                case "DefaultChannelModelMapping":
                    validateJson(value, new TypeReference<Map<Integer, Map<String, String>>>() {}, key);
                    appConfig.setDefaultChannelModelMapping(value);
                    break;
                case "GroupConsumeLevelRatio":
                    validateJson(value, new TypeReference<Map<String, Double>>() {}, key);
                    appConfig.setGroupConsumeLevelRatio(value);
                    break;
                default:
                    logger.warn("No AppConfig mapping found for key '{}' in switch statement. Cannot update.", key);
                    return;
            }
            logger.info("AppConfig updated: {} = {}", key, value);
        } catch (NumberFormatException e) {
            logger.error("Failed to parse value for key '{}': {} - Error: {}. Using current AppConfig default.", key, value, e.getMessage());
            // Optionally re-apply current AppConfig default if parsing fails
            OptionDefaultProvider provider = OPTION_DEFAULTS.get(key);
            if (provider != null) {
                 String defaultValue = provider.defaultValueGetter.apply(appConfig);
                 updateAppConfig(key, defaultValue); // Recursive call with default, be careful with stack overflow if default is also bad
            }
        } catch (IllegalArgumentException e) { // Catch validation errors from JSON validators
            logger.error("Validation failed for key '{}' with value '{}': {}. Using current AppConfig default.", key, value, e.getMessage());
            OptionDefaultProvider provider = OPTION_DEFAULTS.get(key);
            if (provider != null) {
                 String defaultValue = provider.defaultValueGetter.apply(appConfig);
                 updateAppConfig(key, defaultValue);
            }
        } catch (Exception e) {
            logger.error("Unexpected error updating AppConfig for key '{}' with value '{}': {}. Using current AppConfig default.", key, value, e.getMessage(), e);
            OptionDefaultProvider provider = OPTION_DEFAULTS.get(key);
            if (provider != null) {
                 String defaultValue = provider.defaultValueGetter.apply(appConfig);
                 updateAppConfig(key, defaultValue);
            }
        }
    }

    @PostConstruct
    @Transactional
    public void loadOptionsOnStartup() {
        logger.info("Loading options on startup...");
        Map<String, String> optionsFromDB = optionRepository.findAll().stream()
                .collect(Collectors.toMap(OptionEntity::getKey, OptionEntity::getValue));

        OPTION_DEFAULTS.forEach((key, provider) -> {
            String dbValue = optionsFromDB.get(key);
            if (dbValue != null) {
                logger.info("Loading option from DB: {} = {}", key, dbValue);
                updateAppConfig(key, dbValue); // Load DB value into AppConfig
            } else {
                // Option not in DB, get default from AppConfig (which has initial defaults)
                String defaultValueFromAppConfigBean = provider.defaultValueGetter.apply(appConfig);
                logger.info("Option '{}' not found in DB. Initializing with default from AppConfig bean: {}", key, defaultValueFromAppConfigBean);
                
                OptionEntity newOption = new OptionEntity(key, defaultValueFromAppConfigBean);
                optionRepository.save(newOption);
                // AppConfig already holds this default, but calling updateAppConfig ensures consistency
                // especially if there's any transformation/validation logic in updateAppConfig.
                // However, be cautious if updateAppConfig itself tries to fetch default on null,
                // which it does. Here, defaultValueFromAppConfigBean should not be null if defaults are set.
                if (defaultValueFromAppConfigBean != null) {
                    updateAppConfig(key, defaultValueFromAppConfigBean);
                } else {
                     logger.warn("Default value for '{}' from AppConfig bean is null. DB entry created with null.", key);
                }
            }
        });
        logger.info("Finished loading options. AppConfig state: {}", appConfig); // Log final state if feasible
    }
}
