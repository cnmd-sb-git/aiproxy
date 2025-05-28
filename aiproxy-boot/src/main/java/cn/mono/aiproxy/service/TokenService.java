package cn.mono.aiproxy.service;

import cn.mono.aiproxy.config.AppConfig;
import cn.mono.aiproxy.model.GroupEntity;
import cn.mono.aiproxy.model.Log;
import cn.mono.aiproxy.model.Token;
import cn.mono.aiproxy.repository.GroupRepository;
import cn.mono.aiproxy.repository.LogRepository;
import cn.mono.aiproxy.repository.TokenRepository;
import cn.mono.aiproxy.service.dto.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    private static final int TOKEN_NAME_MAX_LENGTH = 50; // Example length
    private static final int GENERATED_KEY_LENGTH = 48;


    private final TokenRepository tokenRepository;
    private final GroupRepository groupRepository;
    private final LogRepository logRepository;
    private final AppConfig appConfig;

    // --- DTO Conversion ---

    public TokenDTO convertToDTO(Token token) {
        if (token == null) return null;
        TokenDTO dto = new TokenDTO();
        BeanUtils.copyProperties(token, dto, "group"); // Exclude group to handle manually
        if (token.getGroup() != null) {
            dto.setGroupId(token.getGroup().getId());
        }

        // Populate accessedAt
        // Assuming token.getName() and token.getGroup().getId() are available
        if (token.getName() != null && token.getGroup() != null && token.getGroup().getId() != null) {
            Optional<Log> lastLog = logRepository.findTopByTokenNameAndGroupIdOrderByRequestAtDesc(token.getName(), token.getGroup().getId());
            lastLog.ifPresent(log -> dto.setAccessedAt(log.getRequestAt()));
        } else {
             logger.warn("Token name or group ID is null for token ID {}. Cannot fetch accessedAt.", token.getId());
        }


        return dto;
    }

    // Converts TokenCreationRequestDTO to Token entity
    private Token convertToEntity(TokenCreationRequestDTO creationDTO, GroupEntity group) {
        if (creationDTO == null) return null;
        Token entity = new Token();
        
        entity.setName(creationDTO.getName());
        entity.setGroup(group);
        entity.setSubnets(creationDTO.getSubnets() != null ? new ArrayList<>(creationDTO.getSubnets()) : new ArrayList<>());
        entity.setModels(creationDTO.getModels() != null ? new ArrayList<>(creationDTO.getModels()) : new ArrayList<>());
        
        if (creationDTO.getExpiredAtEpochMilli() != null) {
            entity.setExpiredAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(creationDTO.getExpiredAtEpochMilli()), ZoneOffset.UTC));
        }
        entity.setQuota(creationDTO.getQuota() != null ? creationDTO.getQuota() : 0.0);
        
        // Default values
        entity.setStatus(1); // Active by default
        entity.setUsedAmount(0.0);
        entity.setRequestCount(0);

        return entity;
    }

    // --- Validation Logic ---

    private void validateTokenCreation(String groupId, TokenCreationRequestDTO dto) {
        if (!StringUtils.hasText(dto.getName())) {
            throw new IllegalArgumentException("Token name must not be empty.");
        }
        if (dto.getName().length() > TOKEN_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Token name exceeds maximum length of " + TOKEN_NAME_MAX_LENGTH + " characters.");
        }
        if (tokenRepository.findByNameAndGroupId(dto.getName(), groupId).isPresent()) {
            throw new DataIntegrityViolationException("Token with name '" + dto.getName() + "' already exists in group '" + groupId + "'.");
        }
        // TODO: Subnet validation using network.IsValidSubnets equivalent
        logger.warn("TODO: Implement subnet validation for token creation.");
    }
    
    private void validateTokenName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Token name must not be empty.");
        }
        if (name.length() > TOKEN_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Token name exceeds maximum length of " + TOKEN_NAME_MAX_LENGTH + " characters.");
        }
    }


    // --- Key Generation ---
    private String generateTokenKey() {
        // Generate a key similar to Go's utils.GenerateKey (default length 48)
        // A simple UUID based approach for now, adjust if specific format is needed.
        String uuidPart = UUID.randomUUID().toString().replace("-", ""); // 32 chars
        String uuidPart2 = UUID.randomUUID().toString().replace("-", ""); // 32 chars
        return (uuidPart + uuidPart2).substring(0, GENERATED_KEY_LENGTH);
    }

    // --- Service Methods ---

    @Transactional
    public TokenDTO createTokenInGroup(String groupId, TokenCreationRequestDTO creationDTO, boolean autoCreateGroup, boolean ignoreExisting) {
        validateTokenCreation(groupId, creationDTO);

        GroupEntity group;
        Optional<GroupEntity> groupOpt = groupRepository.findById(groupId);

        if (groupOpt.isPresent()) {
            group = groupOpt.get();
        } else if (autoCreateGroup) {
            logger.info("Group with ID '{}' not found. Auto-creating group.", groupId);
            GroupEntity newGroup = new GroupEntity();
            newGroup.setId(groupId);
            newGroup.setStatus(1); // Default status: enabled
            // Set other defaults for GroupEntity as needed from its definition
            newGroup.setRpmRatio(0.0);
            newGroup.setTpmRatio(0.0);
            newGroup.setUsedAmount(0.0);
            newGroup.setRequestCount(0);
            newGroup.setBalanceAlertEnabled(false);
            newGroup.setAvailableSets(new ArrayList<>());
            group = groupRepository.save(newGroup);
        } else {
            throw new EntityNotFoundException("Group with ID '" + groupId + "' not found.");
        }

        if (ignoreExisting) {
            Optional<Token> existingTokenOpt = tokenRepository.findByNameAndGroupId(creationDTO.getName(), groupId);
            if (existingTokenOpt.isPresent()) {
                logger.info("Token with name '{}' already exists in group '{}' and ignoreExisting is true. Returning existing token.", creationDTO.getName(), groupId);
                return convertToDTO(existingTokenOpt.get());
            }
        }

        long currentTokenCount = tokenRepository.countByGroupId(groupId);
        if (appConfig.getGroupMaxTokenNum() != null && currentTokenCount >= appConfig.getGroupMaxTokenNum()) {
            throw new IllegalStateException("Maximum number of tokens (" + appConfig.getGroupMaxTokenNum() + ") reached for group '" + groupId + "'.");
        }

        Token token = convertToEntity(creationDTO, group);
        token.setKey(generateTokenKey()); // Generate and set unique key

        try {
            token = tokenRepository.save(token);
            logger.info("Created token '{}' with ID {} in group '{}'", token.getName(), token.getId(), groupId);
        } catch (DataIntegrityViolationException e) {
            // Catch potential unique key violation if generateTokenKey is not perfectly unique (though unlikely with UUID based)
            // Or if name+group constraint was somehow missed in validation (concurrent request)
            logger.error("Data integrity violation while saving token: {}", e.getMessage());
            if (tokenRepository.findByKey(token.getKey()).isPresent()) {
                 throw new DataIntegrityViolationException("Generated token key already exists. Please try again.", e);
            }
            throw e; 
        }
        return convertToDTO(token);
    }

    @Transactional(readOnly = true)
    public Page<TokenDTO> getTokens(String groupId, Pageable pageable, Integer status) {
        Specification<Token> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("group").get("id"), groupId));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return tokenRepository.findAll(spec, pageable).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<TokenDTO> searchTokens(String groupId, String keyword, Pageable pageable, Integer status, String name, String key) {
        Specification<Token> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(groupId)) {
                 predicates.add(criteriaBuilder.equal(root.get("group").get("id"), groupId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(name)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(key)) {
                predicates.add(criteriaBuilder.like(root.get("key"), "%" + key + "%"));
            }
            if (StringUtils.hasText(keyword)) {
                Predicate nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + keyword.toLowerCase() + "%");
                Predicate keyMatch = criteriaBuilder.like(root.get("key"), "%" + keyword + "%");
                // TODO: Search in models list (requires proper handling for ElementCollection search)
                // Predicate modelsMatch = criteriaBuilder.isMember(keyword, root.get("models")); // This is for exact match in collection
                // For like search in collection, it's more complex.
                logger.warn("Keyword search on 'models' list is not fully implemented in this basic version.");
                predicates.add(criteriaBuilder.or(nameMatch, keyMatch));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return tokenRepository.findAll(spec, pageable).map(this::convertToDTO);
    }
    
    @Transactional(readOnly = true)
    public Optional<TokenDTO> getTokenById(Integer id) {
        return tokenRepository.findById(id).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Optional<TokenDTO> getTokenByGroupIdAndId(String groupId, Integer id) {
        return tokenRepository.findByIdAndGroupId(id, groupId).map(this::convertToDTO);
    }

    @Transactional
    public Optional<TokenDTO> updateToken(Integer id, TokenUpdateRequestDTO updateDTO) {
        // TODO: Implement full validation similar to validateTokenCreation if strict rules apply on update
        // For now, basic name validation if provided.
        if (updateDTO.getName() != null) {
            validateTokenName(updateDTO.getName());
        }
        
        return tokenRepository.findById(id)
                .map(token -> {
                    if (updateDTO.getName() != null) {
                        // Check for name uniqueness within the group if name is changing
                        if (!token.getName().equals(updateDTO.getName()) && 
                            tokenRepository.findByNameAndGroupId(updateDTO.getName(), token.getGroup().getId()).isPresent()) {
                            throw new DataIntegrityViolationException("Token name '" + updateDTO.getName() + "' already exists in group '" + token.getGroup().getId() + "'.");
                        }
                        token.setName(updateDTO.getName());
                    }
                    if (updateDTO.getSubnets() != null) {
                        token.setSubnets(new ArrayList<>(updateDTO.getSubnets()));
                    }
                    if (updateDTO.getModels() != null) {
                        token.setModels(new ArrayList<>(updateDTO.getModels()));
                    }
                    if (updateDTO.getExpiredAtEpochMilli() != null) {
                        token.setExpiredAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(updateDTO.getExpiredAtEpochMilli()), ZoneOffset.UTC));
                    } else {
                        token.setExpiredAt(null); // Allow clearing expiration
                    }
                    if (updateDTO.getQuota() != null) {
                        token.setQuota(updateDTO.getQuota());
                    }
                    
                    // TODO: Subnet validation
                    logger.warn("TODO: Implement subnet validation for token update.");
                    
                    Token updatedToken = tokenRepository.save(token);
                    logger.info("Updated token with ID {}", id);
                    return convertToDTO(updatedToken);
                });
    }

    @Transactional
    public Optional<TokenDTO> updateTokenInGroup(String groupId, Integer id, TokenUpdateRequestDTO updateDTO) {
        groupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("Group with ID '" + groupId + "' not found."));
        return tokenRepository.findByIdAndGroupId(id, groupId)
                .flatMap(token -> updateToken(id, updateDTO)); // Reuse the general updateToken logic
    }

    @Transactional
    public Optional<TokenDTO> updateTokenStatus(Integer id, int status) {
        return tokenRepository.findById(id)
                .map(token -> {
                    token.setStatus(status);
                    Token updatedToken = tokenRepository.save(token);
                    logger.info("Updated status for token ID {}: {}", id, status);
                    return convertToDTO(updatedToken);
                });
    }
    
    @Transactional
    public Optional<TokenDTO> updateTokenStatusInGroup(String groupId, Integer id, int status) {
         groupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("Group with ID '" + groupId + "' not found."));
         return tokenRepository.findByIdAndGroupId(id, groupId)
                .flatMap(token -> updateTokenStatus(id, status));
    }

    @Transactional
    public Optional<TokenDTO> updateTokenName(Integer id, String name) {
        validateTokenName(name);
        return tokenRepository.findById(id)
                .map(token -> {
                     // Check for name uniqueness within the group
                    if (!token.getName().equals(name) && 
                        tokenRepository.findByNameAndGroupId(name, token.getGroup().getId()).isPresent()) {
                        throw new DataIntegrityViolationException("Token name '" + name + "' already exists in group '" + token.getGroup().getId() + "'.");
                    }
                    token.setName(name);
                    Token updatedToken = tokenRepository.save(token);
                    logger.info("Updated name for token ID {}: {}", id, name);
                    return convertToDTO(updatedToken);
                });
    }
    
    @Transactional
    public Optional<TokenDTO> updateTokenNameInGroup(String groupId, Integer id, String name) {
        groupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("Group with ID '" + groupId + "' not found."));
        return tokenRepository.findByIdAndGroupId(id, groupId)
            .flatMap(token -> updateTokenName(id, name));
    }


    @Transactional
    public void deleteToken(Integer id) {
        if (tokenRepository.existsById(id)) {
            tokenRepository.deleteById(id);
            logger.info("Deleted token with ID: {}", id);
        } else {
            throw new EntityNotFoundException("Token with ID " + id + " not found.");
        }
    }

    @Transactional
    public void deleteTokensByIds(List<Integer> ids) {
        List<Token> tokensToDelete = tokenRepository.findAllById(ids);
        if (!tokensToDelete.isEmpty()) {
             if (tokensToDelete.size() != ids.size()) {
                List<Integer> foundIds = tokensToDelete.stream().map(Token::getId).collect(Collectors.toList());
                List<Integer> notFoundIds = ids.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
                logger.warn("Some tokens not found for deletion: {}", notFoundIds);
             }
            tokenRepository.deleteAllInBatch(tokensToDelete);
            logger.info("Deleted tokens with IDs: {}", ids);
        } else {
             logger.warn("No tokens found for deletion with IDs: {}", ids);
        }
    }

    @Transactional
    public void deleteTokenInGroup(String groupId, Integer id) {
        groupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("Group with ID '" + groupId + "' not found."));
        Token token = tokenRepository.findByIdAndGroupId(id, groupId)
            .orElseThrow(() -> new EntityNotFoundException("Token with ID " + id + " not found in group " + groupId + "."));
        tokenRepository.delete(token);
        logger.info("Deleted token with ID {} in group {}", id, groupId);
    }

    @Transactional
    public void deleteTokensInGroup(String groupId, List<Integer> ids) {
        groupRepository.findById(groupId).orElseThrow(() -> new EntityNotFoundException("Group with ID '" + groupId + "' not found."));
        List<Token> tokensToDelete = tokenRepository.findAllByIdInAndGroupId(ids, groupId);
        if (!tokensToDelete.isEmpty()) {
            if (tokensToDelete.size() != ids.size()) {
                List<Integer> foundIds = tokensToDelete.stream().map(Token::getId).collect(Collectors.toList());
                List<Integer> notFoundIds = ids.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
                logger.warn("Some tokens not found in group {} for deletion: {}", groupId, notFoundIds);
            }
            tokenRepository.deleteAllInBatch(tokensToDelete);
            logger.info("Deleted tokens with IDs {} in group {}", ids, groupId);
        } else {
            logger.warn("No tokens found in group {} for deletion with IDs: {}", groupId, ids);
        }
    }
}
