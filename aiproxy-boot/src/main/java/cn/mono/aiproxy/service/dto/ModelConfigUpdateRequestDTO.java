package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigUpdateRequestDTO {
    // 'model' (PK) is typically part of the path or a separate param, not in the body for update.
    // If it were here, it should match the one being updated and not change the PK itself.
    private String owner; // Optional
    private Integer type; // Optional
    private String config; // Optional, JSON string
    private String imageQualityPrices; // Optional, JSON string
    private String imagePrices; // Optional, JSON string
    private PriceEmbeddable price; // Optional
    private Long rpm; // Optional
    private Long tpm; // Optional
    private Long retryTimes; // Optional
    private Boolean excludeFromTests; // Optional
}
