package cn.mono.aiproxy.controller;

import cn.mono.aiproxy.service.GroupService;
import cn.mono.aiproxy.service.dto.GroupCreationRequestDTO;
import cn.mono.aiproxy.service.dto.GroupDTO;
import cn.mono.aiproxy.service.dto.GroupModelConfigCreationDTO;
import cn.mono.aiproxy.service.dto.GroupModelConfigDTO;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api") // Base path
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    // --- Group Endpoints ---

    @GetMapping("/groups/") // Trailing slash for consistency
    public ResponseEntity<Page<GroupDTO>> getAllGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(groupService.getAllGroups(pageable));
    }

    @GetMapping("/groups/search")
    public ResponseEntity<Page<GroupDTO>> searchGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(groupService.searchGroups(keyword, status, pageable));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<?> getGroupById(@PathVariable String groupId) {
        return groupService.getGroupById(groupId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/group/{groupId}") // Path variable typically identifies the resource for creation with a client-specified ID
    public ResponseEntity<?> createGroup(@PathVariable String groupId, @RequestBody GroupCreationRequestDTO creationDTO) {
        // Ensure the ID in path matches the ID in body, or DTO doesn't have ID and it's taken from path.
        // For simplicity, let's assume the DTO's ID field is used if present, or set from path if DTO's is null.
        if (creationDTO.getId() == null) {
            creationDTO.setId(groupId);
        } else if (!creationDTO.getId().equals(groupId)) {
            return ResponseEntity.badRequest().body("Group ID in path must match ID in request body if provided.");
        }
        
        try {
            GroupDTO createdGroup = groupService.createGroup(creationDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdGroup);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PutMapping("/group/{groupId}")
    public ResponseEntity<?> updateGroup(@PathVariable String groupId, @RequestBody GroupCreationRequestDTO updateDTO) {
        try {
            return groupService.updateGroup(groupId, updateDTO)
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

    @DeleteMapping("/group/{groupId}")
    public ResponseEntity<?> deleteGroup(@PathVariable String groupId) {
        try {
            groupService.deleteGroup(groupId);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/groups/batch_delete")
    public ResponseEntity<?> deleteMultipleGroups(@RequestBody List<String> groupIds) {
        try {
            groupService.deleteGroupsByIds(groupIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during batch deletion: " + e.getMessage());
        }
    }
    
    // Assuming status update payload is like: { "ids": ["group1", "group2"], "status": 1 }
    static class GroupStatusUpdateRequest { public List<String> ids; public int status; }
    @PostMapping("/groups/batch_status")
    public ResponseEntity<?> updateMultipleGroupStatus(@RequestBody GroupStatusUpdateRequest request) {
        if (request.ids == null || request.ids.isEmpty()) {
            return ResponseEntity.badRequest().body("No group IDs provided for status update.");
        }
        try {
            for (String id : request.ids) {
                groupService.updateGroupStatus(id, request.status);
            }
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            // This might happen if one of the IDs is not found.
            // Decide on atomicity or partial success. For now, fail fast.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("One or more groups not found: " + e.getMessage());
        }
    }

    @PostMapping("/group/{groupId}/rpm_ratio")
    public ResponseEntity<?> updateGroupRpmRatio(@PathVariable String groupId, @RequestBody Map<String, Double> payload) {
        Double rpmRatio = payload.get("rpmRatio");
        if (rpmRatio == null) return ResponseEntity.badRequest().body("Missing 'rpmRatio' in payload.");
        return groupService.updateGroupRpmRatio(groupId, rpmRatio)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/group/{groupId}/tpm_ratio")
    public ResponseEntity<?> updateGroupTpmRatio(@PathVariable String groupId, @RequestBody Map<String, Double> payload) {
        Double tpmRatio = payload.get("tpmRatio");
        if (tpmRatio == null) return ResponseEntity.badRequest().body("Missing 'tpmRatio' in payload.");
        return groupService.updateGroupTpmRatio(groupId, tpmRatio)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PostMapping("/group/{groupId}/status")
    public ResponseEntity<?> updateSingleGroupStatus(@PathVariable String groupId, @RequestBody Map<String, Integer> payload) {
        Integer status = payload.get("status");
        if (status == null) return ResponseEntity.badRequest().body("Missing 'status' in payload.");
        return groupService.updateGroupStatus(groupId, status)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    // --- GroupModelConfig Endpoints ---
    // Nested under /api/group/{groupId}/model_configs

    @GetMapping("/group/{groupId}/model_configs/")
    public ResponseEntity<?> getGroupModelConfigs(@PathVariable String groupId) {
        try {
            List<GroupModelConfigDTO> configs = groupService.getGroupModelConfigs(groupId);
            return ResponseEntity.ok(configs);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/group/{groupId}/model_configs/")
    public ResponseEntity<?> saveGroupModelConfigs(@PathVariable String groupId, @RequestBody List<GroupModelConfigCreationDTO> dtoList) {
        try {
            List<GroupModelConfigDTO> savedConfigs = groupService.saveGroupModelConfigs(groupId, dtoList);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedConfigs);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/group/{groupId}/model_configs/{modelName}")
    public ResponseEntity<?> getGroupModelConfig(@PathVariable String groupId, @PathVariable String modelName) {
         try {
            return groupService.getGroupModelConfig(groupId, modelName)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EntityNotFoundException e) { // Group itself not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/group/{groupId}/model_configs/{modelName}")
    public ResponseEntity<?> saveSingleGroupModelConfig(@PathVariable String groupId, @PathVariable String modelName, @RequestBody GroupModelConfigCreationDTO dto) {
        if (dto.getModel() == null) {
            dto.setModel(modelName);
        } else if (!dto.getModel().equals(modelName)) {
            return ResponseEntity.badRequest().body("Model name in path must match model name in request body if provided.");
        }
        try {
            List<GroupModelConfigDTO> savedConfigs = groupService.saveGroupModelConfigs(groupId, Collections.singletonList(dto));
            if (savedConfigs.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not save model config.");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(savedConfigs.get(0));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    // PUT for update (similar to POST for save/createOrUpdate)
    @PutMapping("/group/{groupId}/model_configs/{modelName}")
    public ResponseEntity<?> updateSingleGroupModelConfig(@PathVariable String groupId, @PathVariable String modelName, @RequestBody GroupModelConfigCreationDTO dto) {
        if (dto.getModel() == null) {
            dto.setModel(modelName);
        } else if (!dto.getModel().equals(modelName)) {
            return ResponseEntity.badRequest().body("Model name in path must match model name in request body if provided.");
        }
         try {
            List<GroupModelConfigDTO> savedConfigs = groupService.saveGroupModelConfigs(groupId, Collections.singletonList(dto));
             if (savedConfigs.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not update model config.");
            }
            return ResponseEntity.ok(savedConfigs.get(0)); // 200 OK for update
        } catch (EntityNotFoundException e) { // Group not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @DeleteMapping("/group/{groupId}/model_configs/{modelName}")
    public ResponseEntity<?> deleteGroupModelConfig(@PathVariable String groupId, @PathVariable String modelName) {
        try {
            groupService.deleteGroupModelConfig(groupId, modelName);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
    
    static class ModelNamesList { public List<String> models; }
    @DeleteMapping("/group/{groupId}/model_configs/") // Batch delete for model configs
    public ResponseEntity<?> deleteMultipleGroupModelConfigs(@PathVariable String groupId, @RequestBody ModelNamesList request) {
        if (request.models == null || request.models.isEmpty()) {
            return ResponseEntity.badRequest().body("List of model names to delete is required.");
        }
        try {
            groupService.deleteGroupModelConfigs(groupId, request.models);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) { // Group not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }


    @GetMapping("/groups/ip_groups")
    public ResponseEntity<String> getIpGroups() {
        // Placeholder as per instructions
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("TODO: Implement getIpGroupList");
    }
}
