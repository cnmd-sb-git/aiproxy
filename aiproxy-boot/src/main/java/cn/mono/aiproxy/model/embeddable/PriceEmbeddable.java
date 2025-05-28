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
public class PriceEmbeddable {

    @Column(name = "price_per_request")
    private Double perRequestPrice; // Maps to PerRequestPrice

    @Column(name = "price_input")
    private Double inputPrice; // Maps to InputPrice

    @Column(name = "price_input_unit")
    private Long inputPriceUnit; // Maps to InputPriceUnit

    @Column(name = "price_output")
    private Double outputPrice; // Maps to OutputPrice

    @Column(name = "price_output_unit")
    private Long outputPriceUnit; // Maps to OutputPriceUnit

    @Column(name = "price_image_unit")
    private Double imagePriceUnit; // Maps to ImagePriceUnit

    @Column(name = "price_audio_unit")
    private Double audioPriceUnit; // Maps to AudioPriceUnit

    @Column(name = "price_video_unit")
    private Double videoPriceUnit; // Maps to VideoPriceUnit
}
