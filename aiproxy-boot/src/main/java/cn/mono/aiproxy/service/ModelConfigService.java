package cn.mono.aiproxy.service;

import cn.mono.aiproxy.model.ModelConfig;
import cn.mono.aiproxy.repository.ModelConfigRepository;
import cn.mono.aiproxy.service.dto.ModelConfigCreationRequestDTO;
import cn.mono.aiproxy.service.dto.ModelConfigDTO;
import cn.mono.aiproxy.service.dto.ModelConfigUpdateRequestDTO;

import com.fasterxml.jackson.databind.ObjectMapper; // Injected for potential future use
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfigService.class);
    private final ModelConfigRepository modelConfigRepository;
    private final ObjectMapper objectMapper; // For potential JSON validation/manipulation

    // --- DTO Conversion ---

    public ModelConfigDTO convertToDTO(ModelConfig modelConfig) {
        if (modelConfig == null) return null;
        ModelConfigDTO dto = new ModelConfigDTO();
        BeanUtils.copyProperties(modelConfig, dto);
        // PriceEmbeddable is directly copied by BeanUtils
        // JSON string fields (config, imageQualityPrices, imagePrices) are also directly copied
        return dto;
    }

    // Converts ModelConfigCreationRequestDTO to ModelConfig entity
    private ModelConfig convertToEntity(ModelConfigCreationRequestDTO creationDTO) {
        if (creationDTO == null) return null;
        ModelConfig entity = new ModelConfig();
        BeanUtils.copyProperties(creationDTO, entity);
        // 'model' (PK) is set from DTO
        // PriceEmbeddable is copied by BeanUtils
        // JSON string fields are copied by BeanUtils
        
        // Set default for excludeFromTests if null from DTO
        if (entity.getExcludeFromTests() == null) {
            entity.setExcludeFromTests(false);
        }
        return entity;
    }

    // --- Service Methods ---

    @Transactional
    public ModelConfigDTO createModelConfig(ModelConfigCreationRequestDTO creationDTO) {
        if (modelConfigRepository.existsById(creationDTO.getModel())) {
            throw new IllegalArgumentException("ModelConfig with model '" + creationDTO.getModel() + "' already exists.");
        }
        ModelConfig modelConfig = convertToEntity(creationDTO);
        modelConfig = modelConfigRepository.save(modelConfig);
        // TODO Cache: Invalidate/update cache (e.g., InitModelConfigAndChannelCache)
        logger.warn("TODO: Implement cache invalidation/update for ModelConfig creation: {}", modelConfig.getModel());
        logger.info("Created ModelConfig for model: {}", modelConfig.getModel());
        return convertToDTO(modelConfig);
    }

    @Transactional
    public List<ModelConfigDTO> saveModelConfigs(List<ModelConfigCreationRequestDTO> creationDTOs) {
        List<ModelConfig> entitiesToSave = creationDTOs.stream()
                .map(dto -> {
                    // This logic assumes saveModelConfigs can update existing or create new.
                    // If strictly for creation, add existsById check like in createModelConfig.
                    // For now, it acts like a batch save/update.
                    ModelConfig entity = modelConfigRepository.findById(dto.getModel())
                        .orElse(new ModelConfig()); // Create new if not found
                    
                    // Preserve createdAt if updating
                    LocalDateTime createdAt = entity.getCreatedAt(); 
                    
                    BeanUtils.copyProperties(dto, entity);
                    if (entity.getExcludeFromTests() == null) {
                        entity.setExcludeFromTests(false);
                    }
                    if (createdAt != null) { // If it's an existing entity
                        entity.setCreatedAt(createdAt);
                    }
                    // UpdatedAt will be handled by @UpdateTimestamp if it's an update
                    return entity;
                })
                .collect(Collectors.toList());
        
        List<ModelConfig> savedEntities = modelConfigRepository.saveAll(entitiesToSave);
        // TODO Cache: Invalidate/update cache
        logger.warn("TODO: Implement cache invalidation/update for batch ModelConfig save/update.");
        logger.info("Saved/Updated {} ModelConfigs.", savedEntities.size());
        return savedEntities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ModelConfigDTO> getModelConfigByModel(String model) {
        return modelConfigRepository.findById(model).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ModelConfigDTO> getAllModelConfigs(Pageable pageable) {
        return modelConfigRepository.findAll(pageable).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public List<ModelConfigDTO> getAllModelConfigsList() {
        return modelConfigRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ModelConfigDTO> getModelConfigsByModels(List<String> models) {
        return modelConfigRepository.findAllById(models).stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ModelConfigDTO> searchModelConfigs(String keyword, String modelFilter, String ownerFilter, Pageable pageable) {
        Specification<ModelConfig> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(modelFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("model"), modelFilter));
            }
            if (StringUtils.hasText(ownerFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("owner"), ownerFilter));
            }
            if (StringUtils.hasText(keyword)) {
                // Keyword searches in 'model' (PK) and 'owner'
                Predicate modelMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("model")), "%" + keyword.toLowerCase() + "%");
                Predicate ownerMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("owner")), "%" + keyword.toLowerCase() + "%");
                predicates.add(criteriaBuilder.or(modelMatch, ownerMatch));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return modelConfigRepository.findAll(spec, pageable).map(this::convertToDTO);
    }

    @Transactional
    public ModelConfigDTO updateModelConfig(String model, ModelConfigUpdateRequestDTO updateDTO) {
        ModelConfig existingModelConfig = modelConfigRepository.findById(model)
                .orElseThrow(() -> new EntityNotFoundException("ModelConfig with model '" + model + "' not found."));

        // Preserve createdAt
        LocalDateTime createdAt = existingModelConfig.getCreatedAt();

        // Update fields from DTO if they are provided (not null)
        // BeanUtils.copyProperties can be used if DTO fields are Optional or logic handles nulls.
        // Manual mapping provides more control for partial updates.
        if (updateDTO.getOwner() != null) existingModelConfig.setOwner(updateDTO.getOwner());
        if (updateDTO.getType() != null) existingModelConfig.setType(updateDTO.getType());
        if (updateDTO.getConfig() != null) existingModelConfig.setConfig(updateDTO.getConfig());
        if (updateDTO.getImageQualityPrices() != null) existingModelConfig.setImageQualityPrices(updateDTO.getImageQualityPrices());
        if (updateDTO.getImagePrices() != null) existingModelConfig.setImagePrices(updateDTO.getImagePrices());
        if (updateDTO.getPrice() != null) existingModelConfig.setPrice(updateDTO.getPrice());
        if (updateDTO.getRpm() != null) existingModelConfig.setRpm(updateDTO.getRpm());
        if (updateDTO.getTpm() != null) existingModelConfig.setTpm(updateDTO.getTpm());
        if (updateDTO.getRetryTimes() != null) existingModelConfig.setRetryTimes(updateDTO.getRetryTimes());
        if (updateDTO.getExcludeFromTests() != null) existingModelConfig.setExcludeFromTests(updateDTO.getExcludeFromTests());
        
        existingModelConfig.setCreatedAt(createdAt); // Ensure createdAt is not changed
        // updatedAt will be handled by @UpdateTimestamp

        ModelConfig updatedModelConfig = modelConfigRepository.save(existingModelConfig);
        // TODO Cache: Invalidate/update cache
        logger.warn("TODO: Implement cache invalidation/update for ModelConfig update: {}", model);
        logger.info("Updated ModelConfig for model: {}", model);
        return convertToDTO(updatedModelConfig);
    }

    @Transactional
    public void deleteModelConfig(String model) {
        if (!modelConfigRepository.existsById(model)) {
            throw new EntityNotFoundException("ModelConfig with model '" + model + "' not found.");
        }
        modelConfigRepository.deleteById(model);
        // TODO Cache: Invalidate/update cache
        logger.warn("TODO: Implement cache invalidation/update for ModelConfig deletion: {}", model);
        logger.info("Deleted ModelConfig for model: {}", model);
    }

    @Transactional
    public void deleteModelConfigsByModels(List<String> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        // Ensure all specified models exist before attempting batch deletion to provide better feedback,
        // though deleteAllByIdInBatch won't fail if some IDs are not found.
        List<ModelConfig> existingConfigs = modelConfigRepository.findAllById(models);
        if (existingConfigs.size() != models.size()) {
            List<String> foundModels = existingConfigs.stream().map(ModelConfig::getModel).collect(Collectors.toList());
            List<String> notFoundModels = models.stream().filter(m -> !foundModels.contains(m)).collect(Collectors.toList());
            logger.warn("Some ModelConfigs not found for deletion: {}. Proceeding with deletion for found models.", notFoundModels);
        }

        if (!existingConfigs.isEmpty()) {
            modelConfigRepository.deleteAllInBatch(existingConfigs); // Use existingConfigs to ensure only found entities are targeted
             // TODO Cache: Invalidate/update cache for all deleted models
            logger.warn("TODO: Implement cache invalidation/update for batch ModelConfig deletion: {}", models);
            logger.info("Deleted ModelConfigs for models: {}", models);
        } else {
             logger.info("No ModelConfigs found for models: {} to delete.", models);
        }
    }
}
