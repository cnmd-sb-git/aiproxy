package cn.mono.aiproxy.model;

import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "model_configs") // "config" 可能是保留关键字的一部分
public class ModelConfig {

    @Id
    @Column(length = 255) // 假设模型名称可能很长
    private String model;

    @Column(nullable = false)
    private String owner; // TODO: 稍后考虑使用枚举 ModelOwnerType (例如，SYSTEM, USER)

    @Column(nullable = false)
    private Integer type; // 代表 mode.Mode (例如，CHAT, COMPLETION, EMBEDDING)

    @Lob
    @Column(name = "config_json") // "config" 可能是保留关键字
    private String config; // map[ModelConfigKey]any 的JSON字符串

    @Lob
    @Column(name = "image_quality_prices_json")
    private String imageQualityPrices; // map[string]map[string]float64 的JSON字符串

    @Lob
    @Column(name = "image_prices_json")
    private String imagePrices; // map[string]float64 的JSON字符串

    @Embedded
    private PriceEmbeddable price;

    private Long rpm;

    private Long tpm;

    @Column(name = "retry_times")
    private Long retryTimes;

    @Column(name = "exclude_from_tests", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean excludeFromTests = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
