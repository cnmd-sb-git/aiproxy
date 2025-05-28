package cn.mono.aiproxy.controller;

import cn.mono.aiproxy.service.RelayService;
import cn.mono.aiproxy.service.dto.RelayRequestDTO;
import cn.mono.aiproxy.service.dto.RelayResponseDTO;
import cn.mono.aiproxy.service.exceptions.NoSuitableChannelException;
import cn.mono.aiproxy.service.exceptions.RelayException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest; // For accessing all headers
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RelayController {

    private static final Logger logger = LoggerFactory.getLogger(RelayController.class);
    private final RelayService relayService;
    private final ObjectMapper objectMapper; // For parsing request body to extract model

    // Capture various AI provider paths.
    // Using a common base like /v1 and then specific subpaths or a wildcard.
    // For now, let's define specific common paths.
    // A more generic approach: @PostMapping("/v1/**") or @PostMapping("/api/relay/**")
    // For this task, let's assume a few common paths.
    @PostMapping({"/v1/chat/completions", "/v1/completions", "/v1/embeddings"})
    public ResponseEntity<?> handleRelay(
            @RequestBody String requestBodyString,
            @RequestHeader Map<String, String> httpHeaders, // Spring collects headers here
            HttpServletRequest request // For more detailed request info if needed
            ) {

        // --- Authentication (Simplified for now) ---
        // In a real app, this would be handled by Spring Security.
        // For now, expect a custom header or query param for token and group.
        String tokenKey = httpHeaders.get("x-aiproxy-token");
        String groupIdFromAuth = httpHeaders.get("x-aiproxy-group"); // Simulated authenticated group

        if (tokenKey == null || tokenKey.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or empty X-AIProxy-Token header.");
        }
        if (groupIdFromAuth == null || groupIdFromAuth.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or empty X-AIProxy-Group header (simulated auth).");
        }
        
        // Remove sensitive/internal headers before passing to RelayRequestDTO if they were part of httpHeaders
        Map<String, String> clientHeadersToRelay = new HashMap<>(httpHeaders);
        clientHeadersToRelay.remove("x-aiproxy-token");
        clientHeadersToRelay.remove("x-aiproxy-group");
        // Also remove standard Authorization if it was somehow passed by client and we use our own token scheme
        // clientHeadersToRelay.remove("authorization");


        // --- Extract Model and other info from request body ---
        String model;
        String userId = null; // Optional
        boolean stream = false; // Default, can be overridden by request body

        try {
            JsonNode requestBodyJson = objectMapper.readTree(requestBodyString);
            if (requestBodyJson.has("model")) {
                model = requestBodyJson.get("model").asText();
            } else {
                return ResponseEntity.badRequest().body("Missing 'model' field in request body.");
            }
            if (requestBodyJson.has("user")) {
                userId = requestBodyJson.get("user").asText();
            }
            if (requestBodyJson.has("stream")) {
                stream = requestBodyJson.get("stream").asBoolean(false);
            }
        } catch (JsonProcessingException e) {
            logger.error("Error parsing request body JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid JSON request body: " + e.getMessage());
        }

        RelayRequestDTO relayRequest = RelayRequestDTO.builder()
                .model(model)
                .prompt(null) // Prompt is part of fullRequestBodyString, not separately used by current RelayService
                .stream(stream)
                .userId(userId)
                .headers(clientHeadersToRelay)
                .fullRequestBody(requestBodyString)
                .build();

        try {
            RelayResponseDTO relayResponse = relayService.handleRelayRequest(relayRequest, tokenKey, groupIdFromAuth);

            HttpHeaders responseHeaders = new HttpHeaders();
            if (relayResponse.getHeaders() != null) {
                relayResponse.getHeaders().forEach((key, value) -> {
                    // Filter out headers that should not be passed back or are set by Spring
                    if (!key.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING) &&
                        !key.equalsIgnoreCase(HttpHeaders.CONNECTION) &&
                        !key.equalsIgnoreCase(HttpHeaders.SERVER) &&
                        !key.equalsIgnoreCase(HttpHeaders.DATE)) {
                        responseHeaders.add(key, value);
                    }
                });
            }
            // Ensure content type is set, default to application/json if not provided by upstream
            if (!responseHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
                 responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            }


            return new ResponseEntity<>(relayResponse.getBody(), responseHeaders, HttpStatus.valueOf(relayResponse.getStatusCode()));

        } catch (SecurityException | IllegalStateException e) {
            logger.warn("Authentication/Authorization failed for relay request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (NoSuitableChannelException e) {
            logger.warn("No suitable channel found for relay request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        } catch (RelayException e) {
            logger.error("Relay error: {}", e.getMessage(), e.getCause());
            // Determine status code based on cause or a default for relay issues
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (IOException e) {
            logger.error("IO error during relay: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("IO error during relay: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Relay process interrupted: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Relay process interrupted.");
        } catch (Exception e) {
            logger.error("Unexpected error during relay: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during relay.");
        }
    }
}
