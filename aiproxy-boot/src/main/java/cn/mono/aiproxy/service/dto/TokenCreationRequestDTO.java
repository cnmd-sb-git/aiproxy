package cn.mono.aiproxy.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenCreationRequestDTO {
    private String name;
    private List<String> subnets;
    private List<String> models;
    private Long expiredAtEpochMilli; // nullable
    private Double quota;
}
