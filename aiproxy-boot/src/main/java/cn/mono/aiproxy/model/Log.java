package cn.mono.aiproxy.model;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import cn.mono.aiproxy.model.embeddable.UsageEmbeddable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "logs", indexes = { // "log" 通常是关键字
        @Index(name = "idx_log_request_id", columnList = "request_id"),
        @Index(name = "idx_log_group_id", columnList = "group_id"),
        @Index(name = "idx_log_token_id", columnList = "token_id"),
        @Index(name = "idx_log_channel_id", columnList = "channel_id"),
        @Index(name = "idx_log_ip", columnList = "ip"),
        @Index(name = "idx_log_code", columnList = "code")
})
public class Log {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "request_id")
    private String requestId;

    // 根据提示的灵活性使用直接ID字段，后续可升级为@ManyToOne
    @Column(name = "group_id")
    private String groupId; // 对应 GroupEntity.id (String类型)

    @Column(name = "token_id")
    private Integer tokenId; // 对应 Token.id (Integer类型)

    @Column(name = "token_name")
    private String tokenName;

    @Column(name = "channel_id")
    private Integer channelId; // 对应 Channel.id (Integer类型)

    @Column(nullable = false)
    private String model;

    private String endpoint;

    private String ip;

    @Column(name = "request_user") // "user" 可能是关键字
    private String user;

    private Integer code; // HTTP状态码或内部错误码

    private Integer mode; // 例如，chat, completions, embeddings

    @Lob
    private String content; // 请求/响应内容或错误消息

    @Column(name = "retry_times")
    private Long retryTimes;

    @Column(name = "used_amount")
    private Double usedAmount;

    @Column(name = "ttfb_milliseconds")
    private Long ttfbMilliseconds; // 首字节时间

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "request_at", nullable = false)
    private LocalDateTime requestAt;

    @Column(name = "retry_at")
    private LocalDateTime retryAt;

    @Embedded
    private PriceEmbeddable price;

    @Embedded
    private UsageEmbeddable usage;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "log_metadata", joinColumns = @JoinColumn(name = "log_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata;

    @OneToOne(mappedBy = "log", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true, optional = true)
    private RequestDetail requestDetail;
}
