package cn.mono.aiproxy.service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenDTO {
    private Integer id;
    private String key;
    private String name;
    private String groupId; // To store the ID of the GroupEntity
    private Integer status;
    private Double quota;
    private Double usedAmount;
    private Integer requestCount;
    private List<String> models;
    private List<String> subnets;
    private LocalDateTime createdAt;
    private LocalDateTime expiredAt;
    private LocalDateTime accessedAt; // New field
}
