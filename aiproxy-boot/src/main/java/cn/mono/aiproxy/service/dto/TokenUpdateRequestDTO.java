package cn.mono.aiproxy.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// For now, similar to TokenCreationRequestDTO for simplicity.
// Could be refined with Optional fields for partial updates later.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenUpdateRequestDTO {
    private String name; // Optional for update, but if provided, will be updated
    private List<String> subnets; // Optional
    private List<String> models; // Optional
    private Long expiredAtEpochMilli; // Optional
    private Double quota; // Optional
    // Status is handled by a separate DTO/endpoint typically
}
