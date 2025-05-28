package cn.mono.aiproxy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList; // 为初始化列表而添加

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "groups") // 使用 "groups" 以避免潜在的SQL关键字冲突
public class GroupEntity {

    @Id
    @Column(length = 50) // 假设字符串ID的合理长度
    private String id;

    @Column(nullable = false)
    private Integer status; // 例如，1代表启用，0代表禁用

    @Column(name = "rpm_ratio", columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double rpmRatio = 0.0;

    @Column(name = "tpm_ratio", columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double tpmRatio = 0.0;

    @Column(name = "used_amount", columnDefinition = "DOUBLE PRECISION DEFAULT 0.0")
    private Double usedAmount = 0.0;

    @Column(name = "request_count", columnDefinition = "INTEGER DEFAULT 0")
    private Integer requestCount = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "group_available_sets", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "set_name")
    private List<String> availableSets;

    @Column(name = "balance_alert_enabled", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean balanceAlertEnabled = false;

    @Column(name = "balance_alert_threshold")
    private Double balanceAlertThreshold;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Token> tokens = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GroupModelConfigEntity> groupModelConfigs = new ArrayList<>();
}
