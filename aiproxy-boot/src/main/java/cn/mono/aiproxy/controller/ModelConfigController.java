package cn.mono.aiproxy.controller;

import cn.mono.aiproxy.service.ModelConfigService;
import cn.mono.aiproxy.service.dto.ModelConfigCreationRequestDTO;
import cn.mono.aiproxy.service.dto.ModelConfigDTO;
import cn.mono.aiproxy.service.dto.ModelConfigUpdateRequestDTO;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api") // Base path
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping("/model_configs/") // Trailing slash for consistency
    public ResponseEntity<Page<ModelConfigDTO>> getAllModelConfigs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(modelConfigService.getAllModelConfigs(pageable));
    }
    
    @GetMapping("/model_configs/all")
    public ResponseEntity<List<ModelConfigDTO>> getAllModelConfigsList() {
        return ResponseEntity.ok(modelConfigService.getAllModelConfigsList());
    }

    @GetMapping("/model_configs/search")
    public ResponseEntity<Page<ModelConfigDTO>> searchModelConfigs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String model, // modelFilter
            @RequestParam(required = false) String owner, // ownerFilter
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(modelConfigService.searchModelConfigs(keyword, model, owner, pageable));
    }
    
    // Get specific model config
    @GetMapping("/model_config/{modelName}") // Using modelName to avoid conflict with 'model' request param
    public ResponseEntity<?> getModelConfigByModel(@PathVariable String modelName) {
        return modelConfigService.getModelConfigByModel(modelName)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Create a single model config (path might be POST /api/model_config/ or POST /api/model_configs/ with single item in list)
    // Using POST /api/model_config/ for single creation
    @PostMapping("/model_config/")
    public ResponseEntity<?> createModelConfig(@RequestBody ModelConfigCreationRequestDTO creationDTO) {
        try {
            ModelConfigDTO createdConfig = modelConfigService.createModelConfig(creationDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdConfig);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    // Batch save/update model configs
    @PostMapping("/model_configs/")
    public ResponseEntity<?> saveModelConfigs(@RequestBody List<ModelConfigCreationRequestDTO> dtoList) {
        try {
            List<ModelConfigDTO> savedConfigs = modelConfigService.saveModelConfigs(dtoList);
            return ResponseEntity.ok(savedConfigs); // Can be 200 OK or 201 CREATED depending on if it always creates or updates
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }
    
    @GetMapping("/model_configs/models") // Get configs by a list of model names
    public ResponseEntity<List<ModelConfigDTO>> getModelConfigsByModels(@RequestParam List<String> models) {
        return ResponseEntity.ok(modelConfigService.getModelConfigsByModels(models));
    }


    @PutMapping("/model_config/{modelName}")
    public ResponseEntity<?> updateModelConfig(@PathVariable String modelName, @RequestBody ModelConfigUpdateRequestDTO updateDTO) {
        try {
            ModelConfigDTO updatedConfig = modelConfigService.updateModelConfig(modelName, updateDTO);
            return ResponseEntity.ok(updatedConfig);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @DeleteMapping("/model_config/{modelName}")
    public ResponseEntity<?> deleteModelConfig(@PathVariable String modelName) {
        try {
            modelConfigService.deleteModelConfig(modelName);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/model_configs/batch_delete")
    public ResponseEntity<?> deleteMultipleModelConfigs(@RequestBody List<String> modelNames) {
        try {
            modelConfigService.deleteModelConfigsByModels(modelNames);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Handle cases where some models might not be found, or other issues.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during batch deletion: " + e.getMessage());
        }
    }
}
