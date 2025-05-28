package cn.mono.aiproxy.controller;

import cn.mono.aiproxy.service.TokenService;
import cn.mono.aiproxy.service.dto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api") // Base path
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    // --- Group-Scoped Token Endpoints ---
    // Path: /api/group/{groupId}/tokens...

    @PostMapping("/group/{groupId}/tokens/")
    public ResponseEntity<?> createTokenInGroup(
            @PathVariable String groupId,
            @RequestBody TokenCreationRequestDTO creationDTO,
            @RequestParam(defaultValue = "false") boolean autoCreateGroup,
            @RequestParam(defaultValue = "false") boolean ignoreExisting) {
        try {
            TokenDTO createdToken = tokenService.createTokenInGroup(groupId, creationDTO, autoCreateGroup, ignoreExisting);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdToken);
        } catch (IllegalArgumentException | IllegalStateException | DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @GetMapping("/group/{groupId}/tokens/")
    public ResponseEntity<Page<TokenDTO>> getTokensInGroup(
            @PathVariable String groupId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(tokenService.getTokens(groupId, pageable, status));
    }

    @GetMapping("/group/{groupId}/tokens/search")
    public ResponseEntity<Page<TokenDTO>> searchTokensInGroup(
            @PathVariable String groupId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(tokenService.searchTokens(groupId, keyword, pageable, status, name, key));
    }

    @GetMapping("/group/{groupId}/token/{tokenId}")
    public ResponseEntity<?> getTokenInGroup(@PathVariable String groupId, @PathVariable Integer tokenId) {
        return tokenService.getTokenByGroupIdAndId(groupId, tokenId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/group/{groupId}/token/{tokenId}")
    public ResponseEntity<?> updateTokenInGroup(
            @PathVariable String groupId,
            @PathVariable Integer tokenId,
            @RequestBody TokenUpdateRequestDTO updateDTO) {
        try {
            return tokenService.updateTokenInGroup(groupId, tokenId, updateDTO)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/group/{groupId}/token/{tokenId}")
    public ResponseEntity<?> deleteTokenInGroup(@PathVariable String groupId, @PathVariable Integer tokenId) {
        try {
            tokenService.deleteTokenInGroup(groupId, tokenId);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/group/{groupId}/tokens/batch_delete")
    public ResponseEntity<?> deleteMultipleTokensInGroup(@PathVariable String groupId, @RequestBody List<Integer> tokenIds) {
         try {
            tokenService.deleteTokensInGroup(groupId, tokenIds);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during batch deletion: " + e.getMessage());
        }
    }
    
    @PostMapping("/group/{groupId}/token/{tokenId}/status")
    public ResponseEntity<?> updateTokenStatusInGroup(@PathVariable String groupId, @PathVariable Integer tokenId, @RequestBody TokenStatusUpdateRequestDTO statusDTO) {
        try {
            return tokenService.updateTokenStatusInGroup(groupId, tokenId, statusDTO.getStatus())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
    
    @PostMapping("/group/{groupId}/token/{tokenId}/name")
    public ResponseEntity<?> updateTokenNameInGroup(@PathVariable String groupId, @PathVariable Integer tokenId, @RequestBody TokenNameUpdateRequestDTO nameDTO) {
        try {
            return tokenService.updateTokenNameInGroup(groupId, tokenId, nameDTO.getName())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }


    // --- Global Token Endpoints (less common, usually managed within a group) ---
    // These might be for admin purposes if tokens are ever considered outside a group context,
    // or if searching across all groups is needed.

    @GetMapping("/tokens/search") // Global search across all groups
    public ResponseEntity<Page<TokenDTO>> searchAllTokens(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        // Pass null for groupId to searchTokens to indicate global search
        return ResponseEntity.ok(tokenService.searchTokens(null, keyword, pageable, status, name, key));
    }
    
    @GetMapping("/token/{id}") // Get token by global ID
    public ResponseEntity<?> getTokenById(@PathVariable Integer id) {
        return tokenService.getTokenById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/token/{id}") // Update token by global ID
    public ResponseEntity<?> updateTokenById(@PathVariable Integer id, @RequestBody TokenUpdateRequestDTO updateDTO) {
         try {
            return tokenService.updateToken(id, updateDTO)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) { // Should be handled by service returning Optional
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
    
    @DeleteMapping("/token/{id}") // Delete token by global ID
    public ResponseEntity<?> deleteTokenById(@PathVariable Integer id) {
        try {
            tokenService.deleteToken(id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
    
    @PostMapping("/tokens/batch_delete_global") // Global batch delete
    public ResponseEntity<?> deleteMultipleTokensGlobal(@RequestBody List<Integer> tokenIds) {
         try {
            tokenService.deleteTokensByIds(tokenIds); // Assuming this method exists for global deletion
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during batch deletion: " + e.getMessage());
        }
    }

    @PostMapping("/token/{id}/status") // Update status by global ID
    public ResponseEntity<?> updateTokenStatusById(@PathVariable Integer id, @RequestBody TokenStatusUpdateRequestDTO statusDTO) {
         try {
            return tokenService.updateTokenStatus(id, statusDTO.getStatus())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EntityNotFoundException e) { // Should be handled by service returning Optional
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
    
    @PostMapping("/token/{id}/name") // Update name by global ID
    public ResponseEntity<?> updateTokenNameById(@PathVariable Integer id, @RequestBody TokenNameUpdateRequestDTO nameDTO) {
        try {
            return tokenService.updateTokenName(id, nameDTO.getName())
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) { // Should be handled by service returning Optional
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
