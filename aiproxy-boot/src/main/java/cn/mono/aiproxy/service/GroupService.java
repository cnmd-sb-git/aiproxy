package cn.mono.aiproxy.service;

import cn.mono.aiproxy.model.GroupEntity;
import cn.mono.aiproxy.model.GroupModelConfigEntity;
import cn.mono.aiproxy.model.Log;
import cn.mono.aiproxy.model.embeddable.GroupModelConfigId;
import cn.mono.aiproxy.repository.GroupModelConfigRepository;
import cn.mono.aiproxy.repository.GroupRepository;
import cn.mono.aiproxy.repository.LogRepository;
import cn.mono.aiproxy.service.dto.GroupCreationRequestDTO;
import cn.mono.aiproxy.service.dto.GroupDTO;
import cn.mono.aiproxy.service.dto.GroupModelConfigCreationDTO;
import cn.mono.aiproxy.service.dto.GroupModelConfigDTO;

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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final Logger logger = LoggerFactory.getLogger(GroupService.class);
    private final GroupRepository groupRepository;
    private final GroupModelConfigRepository groupModelConfigRepository;
    private final LogRepository logRepository;
    private final ObjectMapper objectMapper; // For imagePrices JSON

    // --- GroupEntity DTO Conversion ---

    public GroupDTO convertToDTO(GroupEntity groupEntity) {
        if (groupEntity == null) return null;
        GroupDTO dto = new GroupDTO();
        BeanUtils.copyProperties(groupEntity, dto);
        // Populate accessedAt
        Optional<Log> lastLog = logRepository.findTopByGroupIdOrderByRequestAtDesc(groupEntity.getId());
        lastLog.ifPresent(log -> dto.setAccessedAt(log.getRequestAt()));
        return dto;
    }

    public GroupEntity convertToEntity(GroupCreationRequestDTO creationDTO) {
        if (creationDTO == null) return null;
        GroupEntity entity = new GroupEntity();
        entity.setId(creationDTO.getId()); // Set ID from DTO
        entity.setRpmRatio(creationDTO.getRpmRatio() != null ? creationDTO.getRpmRatio() : 0.0);
        entity.setTpmRatio(creationDTO.getTpmRatio() != null ? creationDTO.getTpmRatio() : 0.0);
        entity.setAvailableSets(creationDTO.getAvailableSets() != null ? new ArrayList<>(creationDTO.getAvailableSets()) : new ArrayList<>());
        entity.setBalanceAlertEnabled(creationDTO.getBalanceAlertEnabled() != null ? creationDTO.getBalanceAlertEnabled() : false);
        entity.setBalanceAlertThreshold(creationDTO.getBalanceAlertThreshold());
        // Default status to 1 (enabled) if not provided or if invalid value
        entity.setStatus(creationDTO.getStatus() != null ? creationDTO.getStatus() : 1); 
        
        // Default values from GroupEntity definition
        entity.setUsedAmount(0.0);
        entity.setRequestCount(0);
        return entity;
    }

    // --- GroupModelConfigEntity DTO Conversion ---

    public GroupModelConfigDTO convertToDTO(GroupModelConfigEntity entity) {
        if (entity == null) return null;
        GroupModelConfigDTO dto = new GroupModelConfigDTO();
        BeanUtils.copyProperties(entity, dto);
        if (entity.getId() != null) {
            dto.setGroupId(entity.getId().getGroupId());
            dto.setModel(entity.getId().getModel());
        }
        // PriceEmbeddable is copied by BeanUtils
        // imagePrices (JSON string) is copied by BeanUtils
        return dto;
    }

    public GroupModelConfigEntity convertToEntity(String groupId, GroupModelConfigCreationDTO dto) {
        if (dto == null) return null;
        GroupModelConfigEntity entity = new GroupModelConfigEntity();
        GroupModelConfigId id = new GroupModelConfigId(groupId, dto.getModel());
        entity.setId(id);
        
        BeanUtils.copyProperties(dto, entity, "groupId", "model"); // Exclude id fields already set

        // Ensure PriceEmbeddable is handled if it's complex or needs special mapping
        // For now, assume BeanUtils handles it if field names match or using PriceEmbeddable directly in DTO.
        // If dto.getPrice() is null, entity.getPrice() will be null.
        if (dto.getPrice() != null) {
            entity.setPrice(dto.getPrice());
        } else {
            entity.setPrice(new cn.mono.aiproxy.model.embeddable.PriceEmbeddable()); // Default empty if null
        }


        // imagePrices (JSON string) is copied by BeanUtils
        // If validation or specific object mapping for imagePrices is needed:
        // if (StringUtils.hasText(dto.getImagePrices())) {
        //     try {
        //         // Validate JSON structure if needed
        //         objectMapper.readValue(dto.getImagePrices(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Double>>() {});
        //         entity.setImagePrices(dto.getImagePrices());
        //     } catch (JsonProcessingException e) {
        //         logger.error("Invalid JSON format for imagePrices: {}", dto.getImagePrices(), e);
        //         throw new IllegalArgumentException("Invalid JSON format for imagePrices", e);
        //     }
        // }
        return entity;
    }
    
    // --- GroupEntity Service Methods ---

    @Transactional
    public GroupDTO createGroup(GroupCreationRequestDTO creationDTO) {
        if (creationDTO.getId() == null || creationDTO.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("Group ID must not be empty.");
        }
        if (groupRepository.existsById(creationDTO.getId())) {
            throw new IllegalArgumentException("Group with ID '" + creationDTO.getId() + "' already exists.");
        }
        GroupEntity groupEntity = convertToEntity(creationDTO);
        groupEntity = groupRepository.save(groupEntity);
        logger.info("Created group with ID: {}", groupEntity.getId());
        return convertToDTO(groupEntity);
    }

    @Transactional(readOnly = true)
    public Optional<GroupDTO> getGroupById(String id) {
        return groupRepository.findById(id).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<GroupDTO> getAllGroups(Pageable pageable) {
        return groupRepository.findAll(pageable).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<GroupDTO> searchGroups(String keyword, Integer status, Pageable pageable) {
        Specification<GroupEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                // Assuming keyword searches primarily on group ID (name)
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("id")), "%" + keyword.toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return groupRepository.findAll(spec, pageable).map(this::convertToDTO);
    }

    @Transactional
    public Optional<GroupDTO> updateGroup(String id, GroupCreationRequestDTO updateDTO) {
        return groupRepository.findById(id)
                .map(existingGroup -> {
                    // Update mutable fields
                    existingGroup.setRpmRatio(updateDTO.getRpmRatio() != null ? updateDTO.getRpmRatio() : existingGroup.getRpmRatio());
                    existingGroup.setTpmRatio(updateDTO.getTpmRatio() != null ? updateDTO.getTpmRatio() : existingGroup.getTpmRatio());
                    existingGroup.setAvailableSets(updateDTO.getAvailableSets() != null ? new ArrayList<>(updateDTO.getAvailableSets()) : existingGroup.getAvailableSets());
                    existingGroup.setBalanceAlertEnabled(updateDTO.getBalanceAlertEnabled() != null ? updateDTO.getBalanceAlertEnabled() : existingGroup.isBalanceAlertEnabled());
                    existingGroup.setBalanceAlertThreshold(updateDTO.getBalanceAlertThreshold() != null ? updateDTO.getBalanceAlertThreshold() : existingGroup.getBalanceAlertThreshold());
                    if (updateDTO.getStatus() != null) {
                        existingGroup.setStatus(updateDTO.getStatus());
                    }
                    
                    GroupEntity updatedGroup = groupRepository.save(existingGroup);
                    logger.info("Updated group with ID: {}", updatedGroup.getId());
                    return convertToDTO(updatedGroup);
                });
    }

    @Transactional
    public Optional<GroupDTO> updateGroupStatus(String id, int status) {
        return groupRepository.findById(id)
                .map(group -> {
                    group.setStatus(status);
                    GroupEntity updatedGroup = groupRepository.save(group);
                    logger.info("Updated status for group ID {}: {}", id, status);
                    return convertToDTO(updatedGroup);
                });
    }
    
    @Transactional
    public Optional<GroupDTO> updateGroupRpmRatio(String id, double rpmRatio) {
        return groupRepository.findById(id)
                .map(group -> {
                    group.setRpmRatio(rpmRatio);
                    return convertToDTO(groupRepository.save(group));
                });
    }

    @Transactional
    public Optional<GroupDTO> updateGroupTpmRatio(String id, double tpmRatio) {
        return groupRepository.findById(id)
                .map(group -> {
                    group.setTpmRatio(tpmRatio);
                    return convertToDTO(groupRepository.save(group));
                });
    }


    @Transactional
    public void deleteGroup(String id) {
        // CascadeType.ALL on GroupEntity's @OneToMany for Tokens and GroupModelConfigs
        // should handle deletion of related entities.
        if (groupRepository.existsById(id)) {
            groupRepository.deleteById(id);
            logger.info("Deleted group with ID: {}", id);
        } else {
            logger.warn("Group with ID: {} not found for deletion.", id);
            throw new EntityNotFoundException("Group with ID " + id + " not found.");
        }
    }

    @Transactional
    public void deleteGroupsByIds(List<String> ids) {
        List<GroupEntity> groupsToDelete = groupRepository.findAllById(ids);
        if (!groupsToDelete.isEmpty()) {
            groupRepository.deleteAllInBatch(groupsToDelete); // More efficient
            logger.info("Deleted groups with IDs: {}", ids);
        } else {
            logger.warn("No groups found for deletion with IDs: {}", ids);
        }
    }

    // --- GroupModelConfigEntity Service Methods ---

    @Transactional(readOnly = true)
    public List<GroupModelConfigDTO> getGroupModelConfigs(String groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new EntityNotFoundException("Group with ID " + groupId + " not found.");
        }
        return groupModelConfigRepository.findByGroupId(groupId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<GroupModelConfigDTO> getGroupModelConfig(String groupId, String model) {
        return groupModelConfigRepository.findById(new GroupModelConfigId(groupId, model))
                .map(this::convertToDTO);
    }

    @Transactional
    public List<GroupModelConfigDTO> saveGroupModelConfigs(String groupId, List<GroupModelConfigCreationDTO> configsDTO) {
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group with ID " + groupId + " not found. Cannot save model configs."));

        List<GroupModelConfigEntity> entitiesToSave = configsDTO.stream()
                .map(dto -> {
                    // Check if model config already exists for this group
                    GroupModelConfigId gmcId = new GroupModelConfigId(groupId, dto.getModel());
                    GroupModelConfigEntity entity = groupModelConfigRepository.findById(gmcId)
                        .orElse(new GroupModelConfigEntity()); // Create new if not found
                    
                    entity.setId(gmcId);
                    entity.setGroup(group); // Set the group association

                    BeanUtils.copyProperties(dto, entity, "groupId", "model", "price");
                    if (dto.getPrice() != null) {
                         entity.setPrice(dto.getPrice());
                    } else if (entity.getPrice() == null) { // Only set default if not already set
                        entity.setPrice(new cn.mono.aiproxy.model.embeddable.PriceEmbeddable());
                    }
                    
                    // Handle imagePrices JSON string (assuming it's directly in DTO)
                    entity.setImagePrices(dto.getImagePrices());

                    return entity;
                })
                .collect(Collectors.toList());

        List<GroupModelConfigEntity> savedEntities = groupModelConfigRepository.saveAll(entitiesToSave);
        logger.info("Saved {} model configs for group ID: {}", savedEntities.size(), groupId);
        return savedEntities.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    public void deleteGroupModelConfig(String groupId, String model) {
        GroupModelConfigId id = new GroupModelConfigId(groupId, model);
        if (groupModelConfigRepository.existsById(id)) {
            groupModelConfigRepository.deleteById(id); // Relies on repository's deleteById
            logger.info("Deleted model config for group ID: {}, model: {}", groupId, model);
        } else {
            logger.warn("Model config not found for group ID: {}, model: {} for deletion.", groupId, model);
            throw new EntityNotFoundException("GroupModelConfig with group ID " + groupId + " and model " + model + " not found.");
        }
    }

    @Transactional
    public void deleteGroupModelConfigs(String groupId, List<String> models) {
        List<GroupModelConfigId> idsToDelete = models.stream()
                .map(model -> new GroupModelConfigId(groupId, model))
                .collect(Collectors.toList());
        
        List<GroupModelConfigEntity> configsToDelete = groupModelConfigRepository.findAllById(idsToDelete);
        if(!configsToDelete.isEmpty()){
            groupModelConfigRepository.deleteAllInBatch(configsToDelete);
            logger.info("Deleted model configs for group ID: {}, models: {}", groupId, models);
        } else {
            logger.warn("No model configs found for group ID: {}, models: {} for deletion.", groupId, models);
        }
    }

    // --- TODO ---
    public void getIpGroupList(/* ... params ... */) {
        // TODO: Implement logic for getIpGroupList which involves log aggregation.
        // This typically requires querying and processing Log entities.
        logger.warn("TODO: Implement getIpGroupList - requires log aggregation logic.");
        throw new UnsupportedOperationException("getIpGroupList is not yet implemented.");
    }
}
