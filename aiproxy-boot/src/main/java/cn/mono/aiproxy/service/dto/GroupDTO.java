package cn.mono.aiproxy.service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDTO {
    private String id;
    private Integer status;
    private Double rpmRatio;
    private Double tpmRatio;
    private Double usedAmount;
    private Integer requestCount;
    private List<String> availableSets;
    private Boolean balanceAlertEnabled;
    private Double balanceAlertThreshold;
    private LocalDateTime createdAt;
    private LocalDateTime accessedAt; // New field
}
