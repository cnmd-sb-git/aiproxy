package cn.mono.aiproxy.service;

import cn.mono.aiproxy.config.AppConfig;
import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import cn.mono.aiproxy.model.embeddable.UsageEmbeddable;
import cn.mono.aiproxy.service.dto.*;
import cn.mono.aiproxy.service.exceptions.NoSuitableChannelException;
import cn.mono.aiproxy.service.exceptions.RelayException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelayService {

    private static final Logger logger = LoggerFactory.getLogger(RelayService.class);

    private final ChannelService channelService;
    private final ModelConfigService modelConfigService;
    private final LogService logService;
    private final TokenService tokenService;
    private final GroupService groupService; // Added for group details
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    private void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)) // Default connect timeout, request timeout is separate
                .build();
        logger.info("RelayService HttpClient initialized.");
    }

    public RelayResponseDTO handleRelayRequest(RelayRequestDTO relayRequest, String tokenKey, String groupIdFromAuth)
            throws IOException, InterruptedException {
        
        long requestStartTimeMillis = System.currentTimeMillis();
        TokenDTO token;

        // 1. Authentication/Authorization
        try {
            token = tokenService.getTokenByKey(tokenKey)
                    .orElseThrow(() -> new SecurityException("Invalid token key: " + tokenKey));

            if (!token.getGroupId().equals(groupIdFromAuth)) {
                throw new SecurityException("Token's group ('" + token.getGroupId() + "') does not match authentication context group ('" + groupIdFromAuth + "').");
            }
            if (token.getStatus() != 1) { // Assuming 1 is active
                throw new IllegalStateException("Token '" + token.getName() + "' is not active.");
            }
            // TODO: Check token quota, expiration, allowed models on token, subnets
            logger.warn("TODO: Implement full token validation (quota, expiration, models, subnets) for token: {}", token.getName());

        } catch (SecurityException | IllegalStateException e) {
            LogCreationRequestDTO errorLog = createErrorLogDTO(relayRequest, groupIdFromAuth, tokenKey, e.getClass().getSimpleName() + ": " + e.getMessage(), 401, requestStartTimeMillis, System.currentTimeMillis());
            logService.recordLog(errorLog);
            throw e;
        }
        
        GroupDTO group = groupService.getGroupById(groupIdFromAuth)
            .orElseThrow(() -> {
                 LogCreationRequestDTO errorLog = createErrorLogDTO(relayRequest, groupIdFromAuth, tokenKey, "Authenticated group not found: " + groupIdFromAuth, 403, requestStartTimeMillis, System.currentTimeMillis());
                 logService.recordLog(errorLog);
                 return new SecurityException("Authenticated group not found: " + groupIdFromAuth);
            });


        // 2. Improved Channel Selection Logic
        List<ChannelDTO> allActiveChannels = channelService.getAllChannels().stream()
                .filter(ch -> ch.getStatus() != null && ch.getStatus() == 1)
                .collect(Collectors.toList());

        List<ChannelDTO> candidateChannels = allActiveChannels.stream()
                .filter(ch -> (ch.getModels() != null && ch.getModels().contains(relayRequest.getModel())) ||
                               (ch.getModelMapping() != null && ch.getModelMapping().containsKey(relayRequest.getModel())))
                .filter(ch -> {
                    if (group.getAvailableSets() == null || group.getAvailableSets().isEmpty()) {
                        return true; // Group has no set restrictions, all channels are candidates
                    }
                    if (ch.getSets() == null || ch.getSets().isEmpty()) {
                        return false; // Group has set restrictions, but channel has no sets defined
                    }
                    // Check for intersection
                    return ch.getSets().stream().anyMatch(set -> group.getAvailableSets().contains(set));
                })
                .sorted(Comparator.comparing(ChannelDTO::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                                  .thenComparing(ChannelDTO::getId)) // Consistent tie-breaking
                .collect(Collectors.toList());
        
        // TODO GroupModelConfig: If GroupModelConfig exists for this group and model, it might influence channel choice or parameters.
        logger.warn("TODO: Consider GroupModelConfig for channel selection/parameter override for group {} and model {}", groupIdFromAuth, relayRequest.getModel());

        if (candidateChannels.isEmpty()) {
            throw new NoSuitableChannelException("No suitable active channel found for model: " + relayRequest.getModel() +
                                                 " matching group '" + groupIdFromAuth + "' set restrictions.");
        }
        
        // For retry logic, we might iterate through candidateChannels if the first one fails.
        // For now, pick the first (highest priority).
        ChannelDTO selectedChannel = candidateChannels.get(0); 
        
        logger.info("Relaying request for model '{}' using channel '{}' (ID: {}, Priority: {}) for group '{}'", 
            relayRequest.getModel(), selectedChannel.getName(), selectedChannel.getId(), selectedChannel.getPriority(), groupIdFromAuth);


        // 3. Prepare External Request (Target URL and Headers)
        String targetUrl = selectedChannel.getBaseUrl();
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            logger.error("Channel '{}' (ID: {}) has no base URL configured.", selectedChannel.getName(), selectedChannel.getId());
            throw new RelayException("Channel base URL is not configured for channel: " + selectedChannel.getName());
        }
        URI targetUri = URI.create(targetUrl); 

        // 4. Timeout Handling
        long timeoutSeconds = appConfig.getDefaultApiTimeoutSeconds();
        // TODO: Implement more specific timeout from appConfig.getTimeoutWithModelTypeMap()
        // Map<Integer, Long> modelTypeTimeouts = appConfig.getTimeoutWithModelTypeMap();
        // Integer channelTypeKey = selectedChannel.getType(); // Assuming ChannelDTO has a type field that can map to AppConfig
        // if (modelTypeTimeouts.containsKey(channelTypeKey)) {
        //    timeoutSeconds = modelTypeTimeouts.get(channelTypeKey);
        // }
        logger.warn("TODO: Implement dynamic timeouts based on AppConfig.getTimeoutWithModelType(). Using default: {}s", timeoutSeconds);


        // 5. Execute External Request with Retry Logic
        HttpRequest.Builder requestBuilderTemplate = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofSeconds(timeoutSeconds));
        
        // Copy relevant headers from original request, add new ones
        if (relayRequest.getHeaders() != null) {
            relayRequest.getHeaders().forEach((key, value) -> {
                if (!key.equalsIgnoreCase("Content-Length") && 
                    !key.equalsIgnoreCase("Host") &&
                    !key.equalsIgnoreCase("Authorization") && 
                    !key.toLowerCase().startsWith("x-aiproxy-")) {
                    requestBuilderTemplate.header(key, value);
                }
            });
        }
        requestBuilderTemplate.header("Content-Type", "application/json");
        if (selectedChannel.getApiKey() != null && !selectedChannel.getApiKey().isEmpty()) {
            requestBuilderTemplate.header("Authorization", "Bearer " + selectedChannel.getApiKey());
        }
        
        // The body publisher needs to be fresh for each retry.
        HttpRequest.BodyPublisher requestBodyPublisher = HttpRequest.BodyPublishers.ofString(relayRequest.getFullRequestBody());

        HttpResponse<String> externalResponse = null;
        long ttfbMillis = 0;
        long requestEndTimeMillis;
        int attempts = 0;
        long maxRetries = appConfig.getRetryTimes() != null ? appConfig.getRetryTimes() : 0;

        while (attempts <= maxRetries) {
            attempts++;
            HttpRequest externalRequest = requestBuilderTemplate.copy().POST(requestBodyPublisher).build();
            try {
                long externalCallStartTime = System.currentTimeMillis();
                externalResponse = httpClient.send(externalRequest, HttpResponse.BodyHandlers.ofString());
                ttfbMillis = System.currentTimeMillis() - externalCallStartTime;
                requestEndTimeMillis = System.currentTimeMillis();

                if (externalResponse.statusCode() < 500) { // Not a server-side error on their end
                    break; // Success or client-side error (4xx), no need to retry
                }
                // If it's a 5xx error, and we have retries left
                if (attempts > maxRetries) {
                    logger.error("External API call to {} failed after {} attempts with status {}. Last response: {}",
                                 targetUri, attempts, externalResponse.statusCode(), externalResponse.body());
                    // Log this final failed attempt
                    LogCreationRequestDTO errorLog = createLogDTO(relayRequest, null, selectedChannel, token, groupIdFromAuth, externalResponse.statusCode(),
                        "External API call failed after " + attempts + " attempts. Last response: " + externalResponse.body(),
                        requestStartTimeMillis, requestEndTimeMillis, ttfbMillis, attempts -1 );
                    logService.recordLog(errorLog);
                    throw new RelayException("External AI service failed after " + attempts + " attempts. Status: " + externalResponse.statusCode());
                }
                logger.warn("External API call to {} failed with status {} (attempt {}/{}). Retrying...",
                            targetUri, externalResponse.statusCode(), attempts, maxRetries + 1);

            } catch (HttpTimeoutException e) {
                requestEndTimeMillis = System.currentTimeMillis();
                logger.warn("External API call to {} timed out (attempt {}/{}): {}", targetUri, attempts, maxRetries + 1, e.getMessage());
                if (attempts > maxRetries) {
                    LogCreationRequestDTO errorLog = createLogDTO(relayRequest, null, selectedChannel, token, groupIdFromAuth, 504, // Gateway Timeout
                        "HttpTimeoutException after " + attempts + " attempts: " + e.getMessage(), requestStartTimeMillis, requestEndTimeMillis, ttfbMillis, attempts -1);
                    logService.recordLog(errorLog);
                    throw new RelayException("External AI service timed out after " + attempts + " attempts.", e);
                }
            } catch (IOException e) {
                requestEndTimeMillis = System.currentTimeMillis();
                logger.warn("IOException during external API call to {} (attempt {}/{}): {}", targetUri, attempts, maxRetries + 1, e.getMessage());
                if (attempts > maxRetries) {
                    LogCreationRequestDTO errorLog = createLogDTO(relayRequest, null, selectedChannel, token, groupIdFromAuth, 502, // Bad Gateway
                        "IOException after " + attempts + " attempts: " + e.getMessage(), requestStartTimeMillis, requestEndTimeMillis, ttfbMillis, attempts -1);
                    logService.recordLog(errorLog);
                    throw new RelayException("Error during communication with external AI service after " + attempts + " attempts.", e);
                }
            } catch (InterruptedException e) { // Should not be caught inside loop typically, but handle if send throws it
                Thread.currentThread().interrupt();
                requestEndTimeMillis = System.currentTimeMillis();
                LogCreationRequestDTO errorLog = createLogDTO(relayRequest, null, selectedChannel, token, groupIdFromAuth, 500,
                    "InterruptedException: " + e.getMessage(), requestStartTimeMillis, requestEndTimeMillis, ttfbMillis, attempts -1);
                logService.recordLog(errorLog);
                throw new RelayException("Request to external AI service was interrupted.", e);
            }

            if (attempts <= maxRetries) {
                Thread.sleep(1000 * attempts); // Simple backoff: 1s, 2s, 3s...
            }
        }
        
        if (externalResponse == null) { // Should not happen if loop logic is correct
            throw new RelayException("External AI service call failed after all retries, but no specific response or exception was captured.");
        }
        requestEndTimeMillis = System.currentTimeMillis(); // Update end time after all retries/success

        // 6. Process Response
        RelayResponseDTO relayResponse = RelayResponseDTO.builder()
                .body(externalResponse.body())
                .statusCode(externalResponse.statusCode())
                .headers(externalResponse.headers().map().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join(", ", e.getValue()))))
                .build();

        // 7. Pricing and Usage Estimation
        long inputTokens = 0;
        long outputTokens = 0;
        double usedAmount = 0.0;
        PriceEmbeddable priceDetails = new PriceEmbeddable(0.0, 0.0, 1000L, 0.0, 1000L, 0.0, 0.0, 0.0); // Default

        try {
            // Simplified estimation
            if (relayRequest.getFullRequestBody() != null) {
                inputTokens = relayRequest.getFullRequestBody().length() / 4; // Very rough estimate
            }
            if (externalResponse.body() != null && externalResponse.statusCode() >= 200 && externalResponse.statusCode() < 300) {
                outputTokens = externalResponse.body().length() / 4; // Very rough estimate
            }
            logger.warn("TODO: Implement proper tokenizer for usage estimation. Current estimation is very rough (body length / 4).");

            Optional<ModelConfigDTO> modelConfigOpt = modelConfigService.getModelConfigByModel(relayRequest.getModel());
            if (modelConfigOpt.isPresent() && modelConfigOpt.get().getPrice() != null) {
                ModelConfigDTO modelConfig = modelConfigOpt.get();
                priceDetails = modelConfig.getPrice(); // Use actual price details from config
                double inputPricePerThousand = modelConfig.getPrice().getInputPrice() != null ? modelConfig.getPrice().getInputPrice() : 0.0;
                double outputPricePerThousand = modelConfig.getPrice().getOutputPrice() != null ? modelConfig.getPrice().getOutputPrice() : 0.0;
                
                // Assuming prices are per 1000 tokens. Adjust if per token or other units.
                usedAmount = (inputTokens / 1000.0 * inputPricePerThousand) + (outputTokens / 1000.0 * outputPricePerThousand);
                // TODO: Handle per-request pricing if modelConfig.getPrice().getPerRequestPrice() is set.
                logger.warn("TODO: Handle per-request pricing from ModelConfig if applicable.");
            } else {
                logger.warn("ModelConfig not found for model '{}' or price not set. Using default/zero for pricing.", relayRequest.getModel());
            }
        } catch (Exception e) {
            logger.error("Error during pricing/usage estimation: {}", e.getMessage(), e);
        }
        
        UsageEmbeddable usage = new UsageEmbeddable(inputTokens, outputTokens, inputTokens + outputTokens, 0L, 0L, 0L, 0L); // Assuming no image/audio for now

        // 8. Log Transaction
        LogCreationRequestDTO logDto = createLogDTO(
            relayRequest, relayResponse, selectedChannel, token, groupIdFromAuth, 
            externalResponse.statusCode(), externalResponse.body(), 
            requestStartTimeMillis, requestEndTimeMillis, ttfbMillis, attempts -1, // attempts-1 because last attempt is not a "retry"
            usage, priceDetails, usedAmount
        );
        logService.recordLog(logDto);

        // 9. Streaming TODO
        if (relayRequest.isStream()) {
            logger.warn("Streaming requested but not yet implemented. Returning full response.");
        }

        return relayResponse;
    }

    private LogCreationRequestDTO createLogDTO(
        RelayRequestDTO relayRequest,
        RelayResponseDTO relayResponse, 
        ChannelDTO selectedChannel,
        TokenDTO token, 
        String groupId, 
        int statusCode,
        String responseBodyForLog, 
        long requestStartTimeMillis,
        long requestEndTimeMillis, 
        long ttfbMillis,
        int retryCount,
        UsageEmbeddable usage, // Added
        PriceEmbeddable price, // Added
        double usedAmount      // Added
    ) {
        return new LogCreationRequestDTO(
                UUID.randomUUID().toString(), 
                requestStartTimeMillis,
                null, 
                ttfbMillis,
                groupId,
                statusCode,
                selectedChannel.getId(),
                relayRequest.getModel(),
                token.getId(),
                token.getName(),
                selectedChannel.getBaseUrl(), 
                responseBodyForLog, 
                relayRequest.getHeaders() != null ? relayRequest.getHeaders().get("x-forwarded-for") : null, 
                retryCount,
                relayRequest.getUserId(),
                Map.of("channel_type", selectedChannel.getType() != null ? selectedChannel.getType() : "unknown"), // Example metadata
                price, // Populated with estimated/actual price
                usage, // Populated with estimated/actual usage
                usedAmount, // Populated with estimated/actual amount
                relayRequest.getFullRequestBody(),
                (relayResponse != null ? relayResponse.getBody() : responseBodyForLog), 
                false, 
                false  
        );
    }
    
    // Overload for early error logging where channel/token might not be resolved
     private LogCreationRequestDTO createLogDTO(
        RelayRequestDTO relayRequest,
        RelayResponseDTO relayResponse, 
        ChannelDTO selectedChannel, // Can be null if error before channel selection
        TokenDTO token, // Can be null if error before token validation
        String groupId, 
        int statusCode,
        String responseBodyForLog, 
        long requestStartTimeMillis,
        long requestEndTimeMillis, 
        long ttfbMillis,
        int retryCount
    ) {
        // Default/placeholder usage and price for errors where these are not calculated
        UsageEmbeddable defaultUsage = new UsageEmbeddable(0L, 0L, 0L, 0L, 0L, 0L, 0L);
        PriceEmbeddable defaultPrice = new PriceEmbeddable(0.0, 0.0, 1000L, 0.0, 1000L, 0.0, 0.0, 0.0);
        double defaultUsedAmount = 0.0;

        return createLogDTO(relayRequest, relayResponse, selectedChannel, token, groupId, statusCode, 
                            responseBodyForLog, requestStartTimeMillis, requestEndTimeMillis, ttfbMillis, 
                            retryCount, defaultUsage, defaultPrice, defaultUsedAmount);
    }


    // Helper to create error log DTO when token validation fails very early
    private LogCreationRequestDTO createErrorLogDTO(
        RelayRequestDTO relayRequest, String groupId, String tokenKeyForLog, String errorMessage, int statusCode,
        long requestStartTimeMillis, long requestEndTimeMillis
    ) {
         return new LogCreationRequestDTO(
                UUID.randomUUID().toString(),
                requestStartTimeMillis, null, 0L, groupId, statusCode, null, 
                relayRequest.getModel(), null, 
                "TokenKey: " + tokenKeyForLog, 
                null, 
                errorMessage,
                relayRequest.getHeaders() != null ? relayRequest.getHeaders().get("x-forwarded-for") : null,
                0, relayRequest.getUserId(), null,
                new PriceEmbeddable(0.0, 0.0, 1000L, 0.0, 1000L, 0.0, 0.0, 0.0), 
                new UsageEmbeddable(0L, 0L, 0L, 0L, 0L, 0L, 0L), 
                0.0, 
                relayRequest.getFullRequestBody(), errorMessage, false, false
        );
    }
}
