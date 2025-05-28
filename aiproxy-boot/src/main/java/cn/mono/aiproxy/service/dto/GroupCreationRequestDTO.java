package cn.mono.aiproxy.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupCreationRequestDTO {
    private String id; // Group name/ID
    private Double rpmRatio;
    private Double tpmRatio;
    private List<String> availableSets;
    private Boolean balanceAlertEnabled;
    private Double balanceAlertThreshold;
    private Integer status; // Optional, will default if null in service
}
