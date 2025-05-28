package cn.mono.aiproxy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "request_details")
public class RequestDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", referencedColumnName = "id", nullable = false, unique = true)
    private Log log;

    @Lob
    @Column(name = "request_body")
    private String requestBody;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "request_body_truncated", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean requestBodyTruncated = false;

    @Column(name = "response_body_truncated", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean responseBodyTruncated = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
