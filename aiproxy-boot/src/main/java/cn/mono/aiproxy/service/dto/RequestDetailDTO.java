package cn.mono.aiproxy.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestDetailDTO {
    private Integer id;
    private Integer logId; // To link back to LogDTO if needed, though RequestDetailDTO is usually nested
    private String requestBody;
    private String responseBody;
    private Boolean requestBodyTruncated;
    private Boolean responseBodyTruncated;
    private LocalDateTime createdAt;
}
