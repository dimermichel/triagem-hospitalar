package br.gov.sus.triagem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class TriagemResponseDTO {

    @JsonProperty("triagem_id")
    private String triagemId;

    private String status;

    private String mensagem;

    @JsonProperty("criado_em")
    private Instant criadoEm;

    @JsonProperty("hospital_id")
    private String hospitalId;

    public static TriagemResponseDTO aceito(String triagemId, String hospitalId) {
        TriagemResponseDTO dto = new TriagemResponseDTO();
        dto.triagemId = triagemId;
        dto.status = "AGUARDANDO_CLASSIFICACAO";
        dto.mensagem = "Dados recebidos. Classificação de risco em andamento.";
        dto.criadoEm = Instant.now();
        dto.hospitalId = hospitalId;
        return dto;
    }

    // getters e setters

    public String getTriagemId() { return triagemId; }
    public void setTriagemId(String triagemId) { this.triagemId = triagemId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }

    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }
}
