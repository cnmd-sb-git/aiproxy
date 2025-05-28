package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigCreationRequestDTO {
    private String model;
    private String owner;
    private Integer type;
    private String config; // JSON string
    private String imageQualityPrices; // JSON string
    private String imagePrices; // JSON string
    private PriceEmbeddable price;
    private Long rpm;
    private Long tpm;
    private Long retryTimes;
    private Boolean excludeFromTests;
}
