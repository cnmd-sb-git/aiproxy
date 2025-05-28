package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import cn.mono.aiproxy.model.embeddable.UsageEmbeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogCreationRequestDTO {
    private String requestId;
    private Long requestAtEpochMilli;
    private Long retryAtEpochMilli; // Nullable
    private Long ttfbMilliseconds; // Nullable

    private String groupId;
    private Integer code;
    private Integer channelId; // Nullable
    private String model;
    private Integer tokenId; // Nullable
    private String tokenName; // Nullable
    private String endpoint; // Nullable
    private String content; // Nullable
    private String ip; // Nullable
    private Integer retryTimes;
    private String user; // Nullable
    private Map<String, String> metadata; // Nullable

    private PriceEmbeddable price;
    private UsageEmbeddable usage;
    private Double usedAmount;

    // Fields for RequestDetail
    private String requestBody; // Nullable
    private String responseBody; // Nullable
    private boolean requestBodyTruncated = false;
    private boolean responseBodyTruncated = false;
}
