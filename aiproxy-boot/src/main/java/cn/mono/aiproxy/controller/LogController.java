package cn.mono.aiproxy.controller;

import cn.mono.aiproxy.service.LogService;
import cn.mono.aiproxy.service.dto.LogDTO;
import cn.mono.aiproxy.service.dto.RequestDetailDTO; // Assuming this is used for response
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api") // Base path
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping("/logs/search")
    public ResponseEntity<Page<LogDTO>> searchLogs(
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) Integer tokenId,
            @RequestParam(required = false) String tokenName,
            @RequestParam(required = false) Integer channelId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Integer codeType, // 0=all, 1=ok(2xx), 2=error(!2xx)
            @RequestParam(required = false) Integer code,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean withBody,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<LogDTO> logs = logService.searchLogs(
                groupId, startTime, endTime, modelName, tokenId, tokenName,
                channelId, requestId, codeType, code, ip, user, keyword, withBody, pageable);
        return ResponseEntity.ok(logs);
    }
    
    // Simpler GET for all logs (less filters)
    @GetMapping("/logs/")
    public ResponseEntity<Page<LogDTO>> getAllLogs(
            @RequestParam(defaultValue = "false") boolean withBody,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
         // Call searchLogs with minimal parameters
        Page<LogDTO> logs = logService.searchLogs(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, withBody, pageable);
        return ResponseEntity.ok(logs);
    }


    @GetMapping("/logs/group/{groupId}/models")
    public ResponseEntity<List<String>> getUsedModelsByGroup(
            @PathVariable String groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ResponseEntity.ok(logService.getUsedModels(groupId, startTime, endTime));
    }

    @GetMapping("/logs/models") // Global
    public ResponseEntity<List<String>> getAllUsedModels(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ResponseEntity.ok(logService.getUsedModels(null, startTime, endTime));
    }

    @GetMapping("/logs/group/{groupId}/token_names")
    public ResponseEntity<List<String>> getUsedTokenNamesByGroup(
            @PathVariable String groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ResponseEntity.ok(logService.getUsedTokenNames(groupId, startTime, endTime));
    }

    @GetMapping("/logs/token_names") // Global
    public ResponseEntity<List<String>> getAllUsedTokenNames(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ResponseEntity.ok(logService.getUsedTokenNames(null, startTime, endTime));
    }

    @GetMapping("/log/{logId}/detail")
    public ResponseEntity<?> getLogDetail(@PathVariable Integer logId) {
        return logService.getLogDetail(logId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/log/group/{groupId}/{logId}/detail")
    public ResponseEntity<?> getGroupLogDetail(@PathVariable String groupId, @PathVariable Integer logId) {
         try {
            return logService.getGroupLogDetail(logId, groupId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EntityNotFoundException e) { // If group or log within group not found
            return ResponseEntity.notFound().build();
        }
    }
    
    // Delete old logs - this is an admin/system operation
    // Payload example: { "olderThanTimestamp": "2023-01-01T00:00:00" }
    @PostMapping("/logs/delete_old")
    public ResponseEntity<?> deleteOldLogs(@RequestBody Map<String, String> payload) {
        String timestampStr = payload.get("olderThanTimestamp");
        if (timestampStr == null) {
            return ResponseEntity.badRequest().body("Missing 'olderThanTimestamp' in request body.");
        }
        try {
            LocalDateTime olderThan = LocalDateTime.parse(timestampStr); // Assumes ISO_LOCAL_DATE_TIME
            logService.deleteOldLogs(olderThan);
            return ResponseEntity.ok().body("Old logs deletion process initiated for logs older than " + olderThan);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting old logs: " + e.getMessage());
        }
    }
    
    @GetMapping("/logs/consume_error/search")
    public ResponseEntity<String> searchConsumeError() {
        // Placeholder as per instructions
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("TODO: Implement searchConsumeError");
    }
}
