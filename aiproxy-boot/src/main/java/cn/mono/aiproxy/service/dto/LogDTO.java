package cn.mono.aiproxy.service.dto;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import cn.mono.aiproxy.model.embeddable.UsageEmbeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogDTO {
    private Integer id;
    private String requestId;
    private String groupId;
    private Integer tokenId;
    private String tokenName;
    private Integer channelId;
    private String model;
    private String endpoint;
    private String ip;
    private String user;
    private Integer code;
    private Integer mode;
    private String content;
    private Long retryTimes;
    private Double usedAmount;
    private Long ttfbMilliseconds;
    private LocalDateTime createdAt;
    private LocalDateTime requestAt;
    private LocalDateTime retryAt;
    private PriceEmbeddable price; // Or PriceDTO
    private UsageEmbeddable usage; // Or UsageDTO
    private Map<String, String> metadata;
    private RequestDetailDTO requestDetail; // Optional, populated if requested
}
