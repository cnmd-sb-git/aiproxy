package cn.mono.aiproxy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_token_key", columnNames = {"key"}),
        @UniqueConstraint(name = "uk_token_name_group", columnNames = {"name", "group_id"})
})
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "key", nullable = false, unique = true, length = 48)
    private String key;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", referencedColumnName = "id", nullable = false)
    private GroupEntity group;

    @Column(nullable = false)
    private Integer status; // 例如，1代表激活，0代表禁用

    @Column(columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double quota = 0.0;

    @Column(name = "used_amount", columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double usedAmount = 0.0;

    @Column(name = "request_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer requestCount = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "token_models", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "model")
    private List<String> models;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "token_subnets", joinColumns = @JoinColumn(name = "token_id"))
    @Column(name = "subnet")
    private List<String> subnets;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;
}
