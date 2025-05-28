package cn.mono.aiproxy.controller;

import cn.mono.aiproxy.service.ChannelService;
import cn.mono.aiproxy.service.dto.ChannelCreationRequestDTO;
import cn.mono.aiproxy.service.dto.ChannelDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
// Using a base path like /api/channels for collection-level operations
// and /api/channel for individual or specific operations can be an option.
// For simplicity and to match Go structure, mixing might occur.
// Let's use /api for base, then specific paths.
@RequestMapping("/api") 
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping("/channels/type_metas")
    public ResponseEntity<Map<String, Object>> getChannelTypeMetas() {
        return ResponseEntity.ok(channelService.getChannelTypeMetas());
    }

    // Search and list all with pagination (can serve as primary GET for /channels)
    @GetMapping("/channels/") // Note trailing slash to distinguish from /channels/type_metas etc.
    public ResponseEntity<Page<ChannelDTO>> searchChannels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String baseUrl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(channelService.searchChannels(keyword, id, name, apiKey, type, baseUrl, pageable));
    }
    
    // Legacy/alternative search path if needed
    @GetMapping("/channels/search")
    public ResponseEntity<Page<ChannelDTO>> searchChannelsAlt(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String baseUrl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(channelService.searchChannels(keyword, id, name, apiKey, type, baseUrl, pageable));
    }


    @GetMapping("/channels/all")
    public ResponseEntity<List<ChannelDTO>> getAllChannels() {
        return ResponseEntity.ok(channelService.getAllChannels());
    }

    @PostMapping("/channels/") // For batch creation
    public ResponseEntity<?> addChannels(@RequestBody List<ChannelCreationRequestDTO> dtoList) {
        try {
            List<ChannelDTO> createdChannels = channelService.addChannels(dtoList);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdChannels);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }
    
    @GetMapping("/channel/{id}")
    public ResponseEntity<?> getChannelById(@PathVariable Integer id) {
        return channelService.getChannelById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/channel/") // For single creation
    public ResponseEntity<?> addSingleChannel(@RequestBody ChannelCreationRequestDTO dto) {
        try {
            // createChannel service method returns List<ChannelDTO> due to multi-key possibility
            List<ChannelDTO> createdChannels = channelService.createChannel(dto);
            if (createdChannels.isEmpty()) {
                 return ResponseEntity.badRequest().body("Channel could not be created, possibly due to empty API key list.");
            }
            // For single creation context, usually expect one result or an error.
            // If multiple created due to keys, client might need to know.
            // Returning the list for now.
            return ResponseEntity.status(HttpStatus.CREATED).body(createdChannels);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @DeleteMapping("/channel/{id}")
    public ResponseEntity<?> deleteChannel(@PathVariable Integer id) {
        try {
            if (channelService.deleteChannel(id)) {
                return ResponseEntity.ok().build();
            } else {
                // This case might be redundant if service throws EntityNotFoundException
                return ResponseEntity.notFound().build(); 
            }
        } catch (EntityNotFoundException e) { // Should be handled by service layer if applicable
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/channels/batch_delete")
    public ResponseEntity<?> deleteMultipleChannels(@RequestBody List<Integer> ids) {
        try {
            channelService.deleteChannelsByIds(ids);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Handle cases where some IDs might not be found, or other issues.
            // For simplicity, returning 500 for any exception during batch delete.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during batch deletion: " + e.getMessage());
        }
    }

    @PutMapping("/channel/{id}")
    public ResponseEntity<?> updateChannel(@PathVariable Integer id, @RequestBody ChannelCreationRequestDTO updateDTO) {
        try {
            return channelService.updateChannel(id, updateDTO)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }
    
    @PostMapping("/channel/{id}/status")
    public ResponseEntity<?> updateChannelStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> statusPayload) {
        Integer status = statusPayload.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().body("Missing 'status' in request body.");
        }
        try {
            return channelService.updateChannelStatus(id, status)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
