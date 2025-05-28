package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable; // Assuming PriceEmbeddable can be directly used or create a PriceDTO
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDTO {
    private String model;
    private String owner;
    private Integer type;
    private String config; // JSON string
    private String imageQualityPrices; // JSON string
    private String imagePrices; // JSON string
    private PriceEmbeddable price; // Or PriceDTO
    private Long rpm;
    private Long tpm;
    private Long retryTimes;
    private Boolean excludeFromTests;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
