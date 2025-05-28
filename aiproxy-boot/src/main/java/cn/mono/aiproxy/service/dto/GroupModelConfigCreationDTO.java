package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// For now, identical to GroupModelConfigDTO. Can diverge later if needed.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupModelConfigCreationDTO {
    // No groupId here, it will be passed as a path variable
    private String model;
    private boolean overrideLimit;
    private Long rpm;
    private Long tpm;
    private boolean overridePrice;
    private String imagePrices; // JSON string for Map<String, Double>
    private PriceEmbeddable price;
    private boolean overrideRetryTimes;
    private Long retryTimes;
}
