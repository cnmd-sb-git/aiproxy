package cn.mono.aiproxy.controller;

import cn.mono.aiproxy.service.OptionService;
import cn.mono.aiproxy.service.dto.OptionDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/options")
@RequiredArgsConstructor
public class OptionController {

    private final OptionService optionService;

    @GetMapping("/")
    public ResponseEntity<List<OptionDTO>> getAllOptions() {
        return ResponseEntity.ok(optionService.getAllOptions());
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> getOptionByKey(@PathVariable String key) {
        try {
            return optionService.getOptionByKey(key)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/")
    public ResponseEntity<?> updateOptions(@RequestBody Map<String, String> optionsMap) {
        try {
            List<OptionDTO> updatedOptions = optionService.updateOptions(optionsMap);
            return ResponseEntity.ok(updatedOptions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }
    
    @PutMapping("/{key}")
    public ResponseEntity<?> updateOptionByKey(@PathVariable String key, @RequestBody String value) {
        try {
            OptionDTO optionDTO = new OptionDTO(key, value);
            OptionDTO updatedOption = optionService.updateOption(optionDTO);
            return ResponseEntity.ok(updatedOption);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (EntityNotFoundException e) { // Should not happen with updateOption's logic (it creates if not exists)
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred: " + e.getMessage());
        }
    }
}
