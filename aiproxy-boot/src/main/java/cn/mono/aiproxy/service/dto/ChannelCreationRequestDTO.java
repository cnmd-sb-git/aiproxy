package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.dto.ChannelConfigDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCreationRequestDTO {
    private String name;
    private String type; // Consider an Enum later: OPEN_AI, AZURE, CLAUDE, etc.
    private Integer status;
    private String apiKey; // Can contain multiple keys separated by newlines
    private String baseUrl;
    private List<String> models;
    private Map<String, String> modelMapping;
    private Integer priority;
    private ChannelConfigDTO config; // Reusing the DTO from model.dto
    private List<String> sets;
}
