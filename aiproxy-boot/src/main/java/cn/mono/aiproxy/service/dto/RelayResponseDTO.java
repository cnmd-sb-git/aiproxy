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
public class RelayResponseDTO {
    private String body; // JSON response from external AI service
    private int statusCode;
    private Map<String, String> headers;
}
