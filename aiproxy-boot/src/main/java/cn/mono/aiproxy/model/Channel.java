package cn.mono.aiproxy.model;

import cn.mono.aiproxy.model.dto.ChannelConfigDTO; // 后续将创建，但现在导入
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "channels") // 使用 "channels" 以避免潜在的SQL关键字冲突
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String type; // 稍后考虑使用枚举: OPEN_AI, AZURE, CLAUDE 等

    @Column(nullable = false)
    private Integer status; // 例如，1代表启用，0代表禁用

    @Column(name = "base_url")
    private String baseUrl;

    @Lob // 用于存储可能很长的API密钥
    @Column(name = "api_key")
    private String apiKey; // 对应Go代码中的 "Key"

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "channel_models", joinColumns = @JoinColumn(name = "channel_id"))
    @Column(name = "model")
    private List<String> models;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "channel_model_mappings", joinColumns = @JoinColumn(name = "channel_id"))
    @MapKeyColumn(name = "source_model")
    @Column(name = "target_model")
    private Map<String, String> modelMapping;

    private Double balance;

    private Integer priority;

    @Lob
    @Column(name = "config")
    private String config; // ChannelConfigDTO的JSON字符串

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "channel_sets", joinColumns = @JoinColumn(name = "channel_id"))
    @Column(name = "set_name") // "set" 通常是关键字
    private List<String> sets;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_test_error_at")
    private LocalDateTime lastTestErrorAt;

    @Column(name = "balance_updated_at")
    private LocalDateTime balanceUpdatedAt;

    @Column(name = "enabled_auto_balance_check", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean enabledAutoBalanceCheck = false;

    @Column(name = "balance_threshold")
    private Double balanceThreshold;

    @Column(name = "used_amount", columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double usedAmount = 0.0;

    @Column(name = "request_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer requestCount = 0;

    // 如果需要手动设置更新时间戳，则使用PrePersist和PreUpdate方法
    // 例如，由于某种原因未使用@UpdateTimestamp时
}
