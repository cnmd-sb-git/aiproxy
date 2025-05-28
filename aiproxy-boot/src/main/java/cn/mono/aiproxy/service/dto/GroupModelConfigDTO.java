package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupModelConfigDTO {
    private String groupId;
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
