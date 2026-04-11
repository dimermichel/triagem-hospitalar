package br.gov.sus.manchester.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SinaisVitaisDTO {

    @JsonProperty("pressao_sistolica")
    private Integer pressaoSistolica;

    @JsonProperty("pressao_diastolica")
    private Integer pressaoDiastolica;

    @JsonProperty("frequencia_cardiaca")
    private Integer frequenciaCardiaca;

    private Double temperatura;

    @JsonProperty("saturacao_oxigenio")
    private Integer saturacaoOxigenio;

    @JsonProperty("frequencia_respiratoria")
    private Integer frequenciaRespiratoria;

    private Integer glicemia;

    public Integer getPressaoSistolica() { return pressaoSistolica; }
    public void setPressaoSistolica(Integer v) { this.pressaoSistolica = v; }

    public Integer getPressaoDiastolica() { return pressaoDiastolica; }
    public void setPressaoDiastolica(Integer v) { this.pressaoDiastolica = v; }

    public Integer getFrequenciaCardiaca() { return frequenciaCardiaca; }
    public void setFrequenciaCardiaca(Integer v) { this.frequenciaCardiaca = v; }

    public Double getTemperatura() { return temperatura; }
    public void setTemperatura(Double v) { this.temperatura = v; }

    public Integer getSaturacaoOxigenio() { return saturacaoOxigenio; }
    public void setSaturacaoOxigenio(Integer v) { this.saturacaoOxigenio = v; }

    public Integer getFrequenciaRespiratoria() { return frequenciaRespiratoria; }
    public void setFrequenciaRespiratoria(Integer v) { this.frequenciaRespiratoria = v; }

    public Integer getGlicemia() { return glicemia; }
    public void setGlicemia(Integer v) { this.glicemia = v; }
}
