package cn.mono.aiproxy.service;

import cn.mono.aiproxy.model.Channel;
import cn.mono.aiproxy.model.ModelConfig; // For checking existence
import cn.mono.aiproxy.model.dto.ChannelConfigDTO;
import cn.mono.aiproxy.repository.ChannelRepository;
import cn.mono.aiproxy.repository.ModelConfigRepository;
import cn.mono.aiproxy.service.dto.ChannelCreationRequestDTO;
import cn.mono.aiproxy.service.dto.ChannelDTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private static final Logger logger = LoggerFactory.getLogger(ChannelService.class);
    private final ChannelRepository channelRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final ObjectMapper objectMapper; // For ChannelConfigDTO serialization/deserialization

    // --- DTO Conversion ---

    public ChannelDTO convertToDTO(Channel channel) {
        if (channel == null) return null;
        ChannelDTO dto = new ChannelDTO();
        BeanUtils.copyProperties(channel, dto, "config"); // Exclude config for manual mapping

        if (StringUtils.hasText(channel.getConfig())) {
            try {
                // Assuming ChannelDTO.config is String to hold JSON
                // If ChannelDTO.config were ChannelConfigDTO object:
                // ChannelConfigDTO configDTO = objectMapper.readValue(channel.getConfig(), ChannelConfigDTO.class);
                // dto.setConfig(configDTO); 
                dto.setConfig(channel.getConfig()); // Keep as JSON string in DTO as per current DTO structure
            } catch (/*JsonProcessing*/Exception e) { // Catch general exception if JSON processing changes
                logger.error("Failed to deserialize ChannelConfigDTO from JSON: {}", channel.getConfig(), e);
                dto.setConfig(null); // Or handle error appropriately
            }
        }
        return dto;
    }

    // Converts ChannelDTO to Channel entity (used for general purpose, less for creation/update now)
    public Channel convertToEntity(ChannelDTO channelDTO) {
        if (channelDTO == null) return null;
        Channel entity = new Channel();
        BeanUtils.copyProperties(channelDTO, entity, "config");

        if (channelDTO.getConfig() != null) { // Assuming ChannelDTO.config is String (JSON)
             entity.setConfig(channelDTO.getConfig());
            // If ChannelDTO.config were ChannelConfigDTO object:
            // try {
            //     String configJson = objectMapper.writeValueAsString(channelDTO.getConfig());
            //     entity.setConfig(configJson);
            // } catch (JsonProcessingException e) {
            //     logger.error("Failed to serialize ChannelConfigDTO to JSON", e);
            //     // Handle error, maybe throw an exception or set config to null/default
            // }
        }
        return entity;
    }
    
    // Converts ChannelCreationRequestDTO to Channel entity
    private Channel convertToEntity(ChannelCreationRequestDTO creationDTO, String apiKeyOverride) {
        if (creationDTO == null) return null;
        Channel entity = new Channel();
        
        entity.setName(creationDTO.getName());
        entity.setType(creationDTO.getType());
        entity.setStatus(creationDTO.getStatus());
        entity.setApiKey(apiKeyOverride != null ? apiKeyOverride : creationDTO.getApiKey()); // Handle single/multiple keys
        entity.setBaseUrl(creationDTO.getBaseUrl());
        entity.setModels(creationDTO.getModels() != null ? new ArrayList<>(creationDTO.getModels()) : new ArrayList<>());
        entity.setModelMapping(creationDTO.getModelMapping() != null ? new java.util.HashMap<>(creationDTO.getModelMapping()) : new java.util.HashMap<>());
        entity.setPriority(creationDTO.getPriority());
        entity.setSets(creationDTO.getSets() != null ? new ArrayList<>(creationDTO.getSets()) : new ArrayList<>());
        
        // Default values from Channel entity definition
        entity.setEnabledAutoBalanceCheck(false);
        entity.setUsedAmount(0.0);
        entity.setRequestCount(0);


        if (creationDTO.getConfig() != null) {
            try {
                String configJson = objectMapper.writeValueAsString(creationDTO.getConfig());
                entity.setConfig(configJson);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize ChannelConfigDTO to JSON for channel: {}", creationDTO.getName(), e);
                throw new IllegalArgumentException("Invalid ChannelConfigDTO format", e);
            }
        }
        return entity;
    }

    // --- Helper Methods ---

    private void checkModelConfigsExist(List<String> models) {
        if (models == null || models.isEmpty()) {
            return; // No models to check
        }
        for (String modelName : models) {
            if (!modelConfigRepository.existsById(modelName)) {
                logger.error("ModelConfig not found for model: {}", modelName);
                throw new IllegalArgumentException("Configuration for model '" + modelName + "' not found.");
            }
        }
    }

    // --- Service Methods ---

    @Transactional(readOnly = true)
    public List<ChannelDTO> getAllChannels() {
        return channelRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ChannelDTO> getChannelById(Integer id) {
        return channelRepository.findById(id).map(this::convertToDTO);
    }
    
    @Transactional(readOnly = true)
    public Optional<ChannelDTO> getChannelByName(String name) { // Added from previous sketch
        return channelRepository.findByName(name).map(this::convertToDTO);
    }

    @Transactional
    public List<ChannelDTO> createChannel(ChannelCreationRequestDTO creationDTO) {
        logger.info("Creating channel(s) with name: {}", creationDTO.getName());
        checkModelConfigsExist(creationDTO.getModels());
        // TODO: Adaptor-specific key validation (from Go's adaptor.KeyValidator)
        logger.warn("TODO: Implement adaptor-specific key validation for channel type: {}", creationDTO.getType());

        List<Channel> channelsToSave = new ArrayList<>();
        String[] apiKeys = creationDTO.getApiKey() != null ? creationDTO.getApiKey().split("\n") : new String[]{null};
        
        if (apiKeys.length == 0) { // Handle case where apiKey is empty string, resulting in one key which is empty
            apiKeys = new String[]{null};
        }

        for (String key : apiKeys) {
            String trimmedKey = (key != null) ? key.trim() : null;
            if (apiKeys.length > 1 && (!StringUtils.hasText(trimmedKey))) {
                // If multiple keys are provided, skip empty ones.
                // If only one key is provided (even if empty/null), proceed to create one channel.
                continue;
            }
            
            Channel channel = convertToEntity(creationDTO, trimmedKey);
            if (apiKeys.length > 1) {
                // Append a suffix to name if multiple channels are created from one DTO
                // This behavior might need adjustment based on exact requirements from Go version
                // For now, let's assume names should be unique or handled by DB constraints.
                // The Go version seems to create them with the same name, relying on other distinct properties or DB leniency.
                // Let's keep the name as is from DTO for now.
            }
            channelsToSave.add(channel);
        }
        
        if (channelsToSave.isEmpty()) {
             // This case might happen if API keys were provided but all were empty strings.
            if (creationDTO.getApiKey() != null && creationDTO.getApiKey().contains("\n")) {
                 throw new IllegalArgumentException("All API keys provided were empty. No channels created.");
            } else { // Single empty key, create one channel with that empty key
                Channel channel = convertToEntity(creationDTO, creationDTO.getApiKey());
                channelsToSave.add(channel);
            }
        }


        List<Channel> savedChannels = channelRepository.saveAll(channelsToSave);
        logger.info("Successfully created {} channel(s) with base name: {}", savedChannels.size(), creationDTO.getName());
        return savedChannels.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public List<ChannelDTO> addChannels(List<ChannelCreationRequestDTO> creationDTOs) {
        List<Channel> allChannelsToSave = new ArrayList<>();
        for (ChannelCreationRequestDTO dto : creationDTOs) {
            checkModelConfigsExist(dto.getModels());
            // TODO: Adaptor-specific key validation
            logger.warn("TODO: Implement adaptor-specific key validation for channel type: {}", dto.getType());
            
            String[] apiKeys = dto.getApiKey() != null ? dto.getApiKey().split("\n") : new String[]{null};
             if (apiKeys.length == 0) apiKeys = new String[]{null};

            for (String key : apiKeys) {
                 String trimmedKey = (key != null) ? key.trim() : null;
                 if (apiKeys.length > 1 && (!StringUtils.hasText(trimmedKey))) continue;
                allChannelsToSave.add(convertToEntity(dto, trimmedKey));
            }
             if (allChannelsToSave.isEmpty() && (dto.getApiKey() != null && dto.getApiKey().contains("\n"))) {
                // No non-empty keys found for this DTO
             } else if (allChannelsToSave.stream().noneMatch(c -> c.getName().equals(dto.getName()))){ // if no channels were added for current DTO
                allChannelsToSave.add(convertToEntity(dto, dto.getApiKey()));
             }
        }
        List<Channel> savedChannels = channelRepository.saveAll(allChannelsToSave);
        return savedChannels.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public Optional<ChannelDTO> updateChannel(Integer id, ChannelCreationRequestDTO updateDTO) {
        logger.info("Updating channel with ID: {}", id);
        return channelRepository.findById(id)
                .map(existingChannel -> {
                    checkModelConfigsExist(updateDTO.getModels());
                    // TODO: Adaptor-specific key validation
                    logger.warn("TODO: Implement adaptor-specific key validation for channel type: {}", updateDTO.getType());
                    
                    // Preserve original creation timestamp and other non-updatable fields
                    LocalDateTime createdAt = existingChannel.getCreatedAt();
                    Double usedAmount = existingChannel.getUsedAmount();
                    Integer requestCount = existingChannel.getRequestCount();
                    LocalDateTime balanceUpdatedAt = existingChannel.getBalanceUpdatedAt();
                    LocalDateTime lastTestErrorAt = existingChannel.getLastTestErrorAt();


                    // Update fields from DTO
                    existingChannel.setName(updateDTO.getName());
                    existingChannel.setType(updateDTO.getType());
                    existingChannel.setStatus(updateDTO.getStatus());
                    existingChannel.setApiKey(updateDTO.getApiKey()); // Assuming single key on update
                    existingChannel.setBaseUrl(updateDTO.getBaseUrl());
                    existingChannel.setModels(updateDTO.getModels() != null ? new ArrayList<>(updateDTO.getModels()) : new ArrayList<>());
                    existingChannel.setModelMapping(updateDTO.getModelMapping() != null ? new java.util.HashMap<>(updateDTO.getModelMapping()) : new java.util.HashMap<>());
                    existingChannel.setPriority(updateDTO.getPriority());
                    existingChannel.setSets(updateDTO.getSets() != null ? new ArrayList<>(updateDTO.getSets()) : new ArrayList<>());

                    if (updateDTO.getConfig() != null) {
                        try {
                            String configJson = objectMapper.writeValueAsString(updateDTO.getConfig());
                            existingChannel.setConfig(configJson);
                        } catch (JsonProcessingException e) {
                            logger.error("Failed to serialize ChannelConfigDTO to JSON for channel ID: {}", id, e);
                            throw new IllegalArgumentException("Invalid ChannelConfigDTO format", e);
                        }
                    } else {
                        existingChannel.setConfig(null);
                    }
                    
                    // Restore non-updatable fields
                    existingChannel.setCreatedAt(createdAt);
                    existingChannel.setUsedAmount(usedAmount);
                    existingChannel.setRequestCount(requestCount);
                    existingChannel.setBalanceUpdatedAt(balanceUpdatedAt);
                    existingChannel.setLastTestErrorAt(lastTestErrorAt);


                    // TODO: Monitor Interaction (monitor.ClearChannelAllModelErrors(id))
                    logger.warn("TODO: Implement monitor interaction - monitor.ClearChannelAllModelErrors({})", id);

                    Channel updatedChannel = channelRepository.save(existingChannel);
                    logger.info("Successfully updated channel with ID: {}", id);
                    return convertToDTO(updatedChannel);
                });
    }

    @Transactional
    public boolean deleteChannel(Integer id) {
        if (channelRepository.existsById(id)) {
            channelRepository.deleteById(id);
            // TODO: Monitor Interaction (monitor.RemoveChannel(id))
            logger.warn("TODO: Implement monitor interaction - monitor.RemoveChannel({})", id);
            logger.info("Successfully deleted channel with ID: {}", id);
            return true;
        }
        logger.warn("Channel with ID: {} not found for deletion.", id);
        return false;
    }

    @Transactional
    public void deleteChannelsByIds(List<Integer> ids) {
        List<Channel> channelsToDelete = channelRepository.findAllById(ids);
        if (!channelsToDelete.isEmpty()) {
            channelRepository.deleteAllInBatch(channelsToDelete); // More efficient for multiple deletions
            for (Integer id : ids) {
                // TODO: Monitor Interaction (monitor.RemoveChannel(id)) for each deleted channel
                logger.warn("TODO: Implement monitor interaction - monitor.RemoveChannel({})", id);
            }
            logger.info("Successfully deleted channels with IDs: {}", ids);
        } else {
            logger.warn("No channels found for deletion with IDs: {}", ids);
        }
    }


    @Transactional
    public Optional<ChannelDTO> updateChannelStatus(Integer id, Integer status) {
        return channelRepository.findById(id)
                .map(channel -> {
                    channel.setStatus(status);
                    // TODO: Monitor Interaction (monitor.UpdateChannelStatus(id, status == common.ChannelStatusEnabled))
                    logger.warn("TODO: Implement monitor interaction - monitor.UpdateChannelStatus({}, status == ENABLED)", id);
                    Channel updatedChannel = channelRepository.save(channel);
                    logger.info("Updated status for channel ID {}: {}", id, status);
                    return convertToDTO(updatedChannel);
                });
    }

    @Transactional(readOnly = true)
    public Page<ChannelDTO> searchChannels(String keyword, Integer id, String name, String apiKey, String channelType, String baseUrl, Pageable pageable) {
        Specification<Channel> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (id != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), id));
            }
            if (StringUtils.hasText(name)) {
                predicates.add(criteriaBuilder.like(root.get("name"), "%" + name + "%"));
            }
            if (StringUtils.hasText(apiKey)) {
                predicates.add(criteriaBuilder.like(root.get("apiKey"), "%" + apiKey + "%"));
            }
            if (StringUtils.hasText(channelType)) {
                predicates.add(criteriaBuilder.equal(root.get("type"), channelType));
            }
            if (StringUtils.hasText(baseUrl)) {
                predicates.add(criteriaBuilder.like(root.get("baseUrl"), "%" + baseUrl + "%"));
            }

            if (StringUtils.hasText(keyword)) {
                Predicate nameMatch = criteriaBuilder.like(root.get("name"), "%" + keyword + "%");
                Predicate apiKeyMatch = criteriaBuilder.like(root.get("apiKey"), "%" + keyword + "%");
                Predicate baseUrlMatch = criteriaBuilder.like(root.get("baseUrl"), "%" + keyword + "%");
                // For models and sets (List<String>), they are stored as JSON strings or in separate tables.
                // If using @ElementCollection, searching might involve subqueries or joins.
                // If stored as JSON string:
                // Predicate modelsMatch = criteriaBuilder.like(root.get("models").as(String.class), "%" + keyword + "%");
                // Predicate setsMatch = criteriaBuilder.like(root.get("sets").as(String.class), "%" + keyword + "%");
                // For now, let's assume basic field search for keyword
                // This needs adjustment based on how List<String> is persisted and searched.
                // For @ElementCollection, searching is more complex with Specifications directly.
                // For simplicity, keyword search on primary text fields for now.
                logger.warn("Keyword search on 'models' and 'sets' lists is not fully implemented with JPA Specifications in this basic version.");

                predicates.add(criteriaBuilder.or(nameMatch, apiKeyMatch, baseUrlMatch));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Channel> channelPage = channelRepository.findAll(spec, pageable);
        return channelPage.map(this::convertToDTO);
    }

    public Map<String, Object> getChannelTypeMetas() {
        // TODO: Adaptor Metas - Replicate logic from adaptors.ChannelMetas
        // This would involve iterating through registered adaptors and getting their metadata.
        logger.warn("TODO: Implement getChannelTypeMetas - Replicate logic from adaptors.ChannelMetas");
        // Placeholder data:
        return Map.of(
            "OpenAI", Map.of("defaultBaseUrl", "https://api.openai.com"),
            "Azure", Map.of("defaultBaseUrl", "https://your-resource.openai.azure.com")
        );
    }
    
    // Placeholder for methods based on core/controller/channel.go
    // public void testChannel(Integer id) { /* ... */ }
    // public void testAllChannels() { /* ... */ }
    // public void deleteAllDisabledChannels() { /* ... */ }

    public void updateAllChannelsBalance() {
        // TODO: Implement logic to iterate through channels and update their balances
        // This will likely involve calling external AI provider APIs via adaptors.
        logger.info("Placeholder: updateAllChannelsBalance called.");
    }

    public void autoTestBannedModels() {
        // TODO: Implement logic to test channels/models that were previously banned.
        // This might involve:
        // 1. Identifying channels/models marked as "banned" or having high error rates.
        // 2. Sending test requests to these.
        // 3. Updating their status based on test results.
        logger.info("Placeholder: autoTestBannedModels called.");
    }
}
