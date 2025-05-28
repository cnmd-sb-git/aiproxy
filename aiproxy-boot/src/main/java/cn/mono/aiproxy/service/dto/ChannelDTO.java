package cn.mono.aiproxy.service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDTO {
    private Integer id;
    private String name;
    private String type;
    private Integer status;
    private String baseUrl;
    private String apiKey; // Keep as String for DTO, masking can be handled at presentation layer
    private List<String> models;
    private Map<String, String> modelMapping;
    private Double balance;
    private Integer priority;
    private String config; // JSON string for ChannelConfigDTO
    private List<String> sets;
    private LocalDateTime createdAt;
    private LocalDateTime lastTestErrorAt;
    private LocalDateTime balanceUpdatedAt;
    private Boolean enabledAutoBalanceCheck;
    private Double balanceThreshold;
    private Double usedAmount;
    private Integer requestCount;
}
