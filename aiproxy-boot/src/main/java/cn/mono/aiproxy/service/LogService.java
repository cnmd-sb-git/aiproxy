package cn.mono.aiproxy.service;

import cn.mono.aiproxy.model.Log;
import cn.mono.aiproxy.model.RequestDetail;
import cn.mono.aiproxy.repository.LogRepository;
import cn.mono.aiproxy.repository.RequestDetailRepository;
import cn.mono.aiproxy.service.dto.LogCreationRequestDTO;
import cn.mono.aiproxy.service.dto.LogDTO;
import cn.mono.aiproxy.service.dto.RequestDetailDTO;

import com.fasterxml.jackson.databind.ObjectMapper; // For potential future use with JSON in content, etc.
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogService {

    private static final Logger logger = LoggerFactory.getLogger(LogService.class);
    private final LogRepository logRepository;
    private final RequestDetailRepository requestDetailRepository;
    private final ObjectMapper objectMapper; // Injected for potential future use
    private final AppConfig appConfig; // Added for detectAndProcessIPGroups

    // --- DTO Conversion ---

    public RequestDetailDTO convertToDTO(RequestDetail requestDetail) {
        if (requestDetail == null) return null;
        RequestDetailDTO dto = new RequestDetailDTO();
        BeanUtils.copyProperties(requestDetail, dto, "log"); // Exclude Log to avoid circular mapping
        if (requestDetail.getLog() != null) {
            dto.setLogId(requestDetail.getLog().getId());
        }
        return dto;
    }

    public RequestDetail convertToEntity(RequestDetailDTO dto, Log logEntity) {
        if (dto == null) return null;
        RequestDetail entity = new RequestDetail();
        BeanUtils.copyProperties(dto, entity, "logId"); // Exclude logId
        entity.setLog(logEntity); // Set the owning side
        return entity;
    }
    
    public LogDTO convertToDTO(Log log, boolean withBody) {
        if (log == null) return null;
        LogDTO dto = new LogDTO();
        // Copy properties from Log to LogDTO
        // Assuming LogDTO has fields like: id, requestId, groupId, tokenId, tokenName, model, endpoint, ip, user, code, mode, content, retryTimes, usedAmount, ttfbMilliseconds, createdAt, requestAt, retryAt, price, usage, metadata
        BeanUtils.copyProperties(log, dto, "requestDetail"); // Exclude requestDetail for manual mapping

        if (withBody && log.getRequestDetail() != null) {
            dto.setRequestDetail(convertToDTO(log.getRequestDetail()));
        }
        return dto;
    }


    // Converts LogCreationRequestDTO to Log entity
    private Log convertToEntity(LogCreationRequestDTO creationDTO) {
        if (creationDTO == null) return null;
        Log entity = new Log();
        
        entity.setRequestId(creationDTO.getRequestId());
        entity.setGroupId(creationDTO.getGroupId());
        entity.setCode(creationDTO.getCode());
        entity.setChannelId(creationDTO.getChannelId());
        entity.setModel(creationDTO.getModel());
        entity.setTokenId(creationDTO.getTokenId());
        entity.setTokenName(creationDTO.getTokenName());
        entity.setEndpoint(creationDTO.getEndpoint());
        entity.setContent(creationDTO.getContent());
        entity.setIp(creationDTO.getIp());
        entity.setRetryTimes(creationDTO.getRetryTimes() != null ? creationDTO.getRetryTimes().longValue() : 0L);
        entity.setUser(creationDTO.getUser());
        entity.setMetadata(creationDTO.getMetadata());
        entity.setPrice(creationDTO.getPrice());
        entity.setUsage(creationDTO.getUsage());
        entity.setUsedAmount(creationDTO.getUsedAmount());
        
        if (creationDTO.getRequestAtEpochMilli() != null) {
            entity.setRequestAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(creationDTO.getRequestAtEpochMilli()), ZoneOffset.UTC));
        } else {
            entity.setRequestAt(LocalDateTime.now(ZoneOffset.UTC)); // Fallback, though requestAt should always be present
        }
        
        if (creationDTO.getRetryAtEpochMilli() != null) {
            entity.setRetryAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(creationDTO.getRetryAtEpochMilli()), ZoneOffset.UTC));
        }
        entity.setTtfbMilliseconds(creationDTO.getTtfbMilliseconds());
        
        // CreatedAt will be set by @CreationTimestamp on Log entity
        // Mode is not explicitly in DTO, might be derived or set based on endpoint/type later
        
        return entity;
    }

    // --- Service Methods ---

    @Transactional
    public LogDTO recordLog(LogCreationRequestDTO creationDTO) {
        Log logEntity = convertToEntity(creationDTO);
        // createdAt is set by JPA @CreationTimestamp

        // Handle RequestDetail
        if (StringUtils.hasText(creationDTO.getRequestBody()) || StringUtils.hasText(creationDTO.getResponseBody())) {
            RequestDetail requestDetailEntity = new RequestDetail();
            requestDetailEntity.setRequestBody(creationDTO.getRequestBody());
            requestDetailEntity.setResponseBody(creationDTO.getResponseBody());
            requestDetailEntity.setRequestBodyTruncated(creationDTO.isRequestBodyTruncated());
            requestDetailEntity.setResponseBodyTruncated(creationDTO.isResponseBodyTruncated());
            // requestDetailEntity's createdAt is also set by @CreationTimestamp
            
            // Associate RequestDetail with Log
            // The Log entity is the owning side of the Log-RequestDetail relationship via mappedBy
            // So, we set logEntity on requestDetailEntity, and requestDetailEntity on logEntity
            logEntity.setRequestDetail(requestDetailEntity);
            requestDetailEntity.setLog(logEntity); 
            // CascadeType.ALL on Log.requestDetail should save RequestDetail when Log is saved.
        }

        Log savedLog = logRepository.save(logEntity);
        logger.info("Recorded log with ID: {} and RequestId: {}", savedLog.getId(), savedLog.getRequestId());
        return convertToDTO(savedLog, true); // Return with body by default for newly created log
    }

    @Transactional(readOnly = true)
    public Page<LogDTO> searchLogs(
            String groupId, LocalDateTime startTime, LocalDateTime endTime, String modelName,
            Integer tokenId, String tokenName, Integer channelId, String requestId,
            Integer codeType, Integer code, String ip, String user, String keyword,
            boolean withBody, Pageable pageable) {

        Specification<Log> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(groupId)) predicates.add(criteriaBuilder.equal(root.get("groupId"), groupId));
            if (startTime != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            if (endTime != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endTime));
            if (StringUtils.hasText(modelName)) predicates.add(criteriaBuilder.equal(root.get("model"), modelName));
            if (tokenId != null) predicates.add(criteriaBuilder.equal(root.get("tokenId"), tokenId));
            if (StringUtils.hasText(tokenName)) predicates.add(criteriaBuilder.equal(root.get("tokenName"), tokenName));
            if (channelId != null) predicates.add(criteriaBuilder.equal(root.get("channelId"), channelId));
            if (StringUtils.hasText(requestId)) predicates.add(criteriaBuilder.equal(root.get("requestId"), requestId));
            
            if (code != null) {
                 predicates.add(criteriaBuilder.equal(root.get("code"), code));
            } else if (codeType != null) { // codeType: 0=all, 1=ok(2xx), 2=error(!2xx)
                if (codeType == 1) { // OK
                    predicates.add(criteriaBuilder.between(root.get("code"), 200, 299));
                } else if (codeType == 2) { // Error
                    predicates.add(criteriaBuilder.not(criteriaBuilder.between(root.get("code"), 200, 299)));
                }
            }

            if (StringUtils.hasText(ip)) predicates.add(criteriaBuilder.equal(root.get("ip"), ip));
            if (StringUtils.hasText(user)) predicates.add(criteriaBuilder.equal(root.get("user"), user));

            if (StringUtils.hasText(keyword)) {
                Predicate modelMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("model")), "%" + keyword.toLowerCase() + "%");
                Predicate tokenNameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("tokenName")), "%" + keyword.toLowerCase() + "%");
                Predicate contentMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("content")), "%" + keyword.toLowerCase() + "%");
                // Note: Searching RequestDetail body with keyword requires a join, more complex.
                // For now, keyword search is on Log entity fields.
                predicates.add(criteriaBuilder.or(modelMatch, tokenNameMatch, contentMatch));
            }
            
            // If withBody is true, we might need to fetch join RequestDetail
            // This is often handled at the DTO conversion stage or by specific query if performance is critical
            // For Pageable queries, explicit fetch join in Specification can be tricky with count query.
            // It's common to fetch IDs first, then details, or rely on lazy loading + DTO conversion.
            // Here, convertToDTO handles fetching if withBody is true.

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Log> logPage = logRepository.findAll(spec, pageable);
        return logPage.map(log -> convertToDTO(log, withBody));
    }

    @Transactional(readOnly = true)
    public List<String> getUsedModels(String groupId, LocalDateTime startTime, LocalDateTime endTime) {
        return logRepository.findDistinctModelByCriteria(groupId, startTime, endTime);
    }

    @Transactional(readOnly = true)
    public List<String> getUsedTokenNames(String groupId, LocalDateTime startTime, LocalDateTime endTime) {
        return logRepository.findDistinctTokenNameByCriteria(groupId, startTime, endTime);
    }

    @Transactional(readOnly = true)
    public Optional<RequestDetailDTO> getLogDetail(Integer logId) {
        return requestDetailRepository.findByLog_Id(logId).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Optional<RequestDetailDTO> getGroupLogDetail(Integer logId, String groupId) {
        return requestDetailRepository.findByLog_IdAndLog_GroupId(logId, groupId).map(this::convertToDTO);
    }

    @Transactional
    public void deleteOldLogs(LocalDateTime olderThanTimestamp) {
        // Cascading delete should handle RequestDetail entities associated with deleted Log entities
        // due to orphanRemoval=true and CascadeType.ALL on Log.requestDetail.
        logRepository.deleteByCreatedAtBefore(olderThanTimestamp);
        logger.info("Deleted logs older than: {}", olderThanTimestamp);
    }

    public void searchConsumeError(/*... params ...*/) {
        // TODO: Implement logic for searchConsumeError
        // This would involve querying Log entities based on error codes or specific content patterns.
        logger.warn("TODO: Implement searchConsumeError - requires specific error identification logic.");
        throw new UnsupportedOperationException("searchConsumeError is not yet implemented.");
    }

    public void detectAndProcessIPGroups() {
        // TODO: Implement logic based on Go's model.GetIPGroups and subsequent processing (banning, notifying).
        // This involves querying logs for IPs using multiple groups within a time window.
        Long ipGroupsThreshold = appConfig.getIpGroupsThreshold();
        Long ipGroupsBanThreshold = appConfig.getIpGroupsBanThreshold();
        logger.info("Placeholder: detectAndProcessIPGroups called. Threshold: {}, BanThreshold: {}", ipGroupsThreshold, ipGroupsBanThreshold);
    }
}
