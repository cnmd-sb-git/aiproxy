package cn.mono.aiproxy.model;

import cn.mono.aiproxy.model.embeddable.GroupModelConfigId;
import cn.mono.aiproxy.model.embeddable.PriceEmbeddable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "group_model_configs")
public class GroupModelConfigEntity {

    @EmbeddedId
    private GroupModelConfigId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", referencedColumnName = "id", insertable = false, updatable = false)
    private GroupEntity group;

    @Column(name = "override_limit", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean overrideLimit = false;

    private Long rpm;

    private Long tpm;

    @Column(name = "override_price", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean overridePrice = false;

    @Lob
    @Column(name = "image_prices_json") // Map<String, Double> 的JSON字符串
    private String imagePrices;

    @Embedded
    private PriceEmbeddable price;

    @Column(name = "override_retry_times", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean overrideRetryTimes = false;

    @Column(name = "retry_times")
    private Long retryTimes;
}
