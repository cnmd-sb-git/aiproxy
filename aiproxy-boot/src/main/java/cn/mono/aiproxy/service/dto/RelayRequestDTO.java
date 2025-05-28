package cn.mono.aiproxy.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelayRequestDTO {
    private String model;
    private String prompt; // Simple for now, can be List<MessageDTO> for chat
    @Builder.Default
    private boolean stream = false;
    private String userId; // Optional
    private Map<String, String> headers;
    private String fullRequestBody; // The complete JSON request body from the client
}
