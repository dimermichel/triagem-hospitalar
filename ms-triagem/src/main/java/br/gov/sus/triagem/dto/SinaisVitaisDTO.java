package br.gov.sus.triagem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
public class SinaisVitaisDTO {

    /** Pressão arterial sistólica (mmHg) */
    @NotNull @Min(50) @Max(300)
    @JsonProperty("pressao_sistolica")
    private Integer pressaoSistolica;

    /** Pressão arterial diastólica (mmHg) */
    @NotNull @Min(20) @Max(200)
    @JsonProperty("pressao_diastolica")
    private Integer pressaoDiastolica;

    /** Frequência cardíaca (bpm) */
    @NotNull @Min(20) @Max(300)
    @JsonProperty("frequencia_cardiaca")
    private Integer frequenciaCardiaca;

    /** Temperatura corporal (°C) */
    @NotNull @DecimalMin("30.0") @DecimalMax("45.0")
    private Double temperatura;

    /** Saturação de oxigênio (%) */
    @NotNull @Min(50) @Max(100)
    @JsonProperty("saturacao_oxigenio")
    private Integer saturacaoOxigenio;

    /** Frequência respiratória (irpm) */
    @Min(5) @Max(60)
    @JsonProperty("frequencia_respiratoria")
    private Integer frequenciaRespiratoria;

    /** Glicemia capilar (mg/dL) — opcional */
    @JsonProperty("glicemia")
    private Integer glicemia;

    // ---- getters e setters ----

    public Integer getPressaoSistolica() { return pressaoSistolica; }
    public void setPressaoSistolica(Integer pressaoSistolica) { this.pressaoSistolica = pressaoSistolica; }

    public Integer getPressaoDiastolica() { return pressaoDiastolica; }
    public void setPressaoDiastolica(Integer pressaoDiastolica) { this.pressaoDiastolica = pressaoDiastolica; }

    public Integer getFrequenciaCardiaca() { return frequenciaCardiaca; }
    public void setFrequenciaCardiaca(Integer frequenciaCardiaca) { this.frequenciaCardiaca = frequenciaCardiaca; }

    public Double getTemperatura() { return temperatura; }
    public void setTemperatura(Double temperatura) { this.temperatura = temperatura; }

    public Integer getSaturacaoOxigenio() { return saturacaoOxigenio; }
    public void setSaturacaoOxigenio(Integer saturacaoOxigenio) { this.saturacaoOxigenio = saturacaoOxigenio; }

    public Integer getFrequenciaRespiratoria() { return frequenciaRespiratoria; }
    public void setFrequenciaRespiratoria(Integer frequenciaRespiratoria) { this.frequenciaRespiratoria = frequenciaRespiratoria; }

    public Integer getGlicemia() { return glicemia; }
    public void setGlicemia(Integer glicemia) { this.glicemia = glicemia; }
}
