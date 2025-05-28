package cn.mono.aiproxy.model.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageEmbeddable {

    @Column(name = "input_tokens")
    private Long inputTokens; // Maps to InputTokens

    @Column(name = "output_tokens")
    private Long outputTokens; // Maps to OutputTokens

    @Column(name = "total_tokens")
    private Long totalTokens; // Maps to TotalTokens

    @Column(name = "image_count")
    private Long imageCount; // Maps to ImageCount

    @Column(name = "tool_calls_count")
    private Long toolCallsCount; // Maps to ToolCallsCount

    @Column(name = "audio_duration_seconds")
    private Long audioDurationSeconds; // Maps to AudioDurationSeconds

    @Column(name = "video_duration_seconds")
    private Long videoDurationSeconds; // Maps to VideoDurationSeconds
}
