package br.gov.sus.consolidador.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AtendimentoDiarioDTO {

    @JsonProperty("triagem_id")      private String triagemId;
    @JsonProperty("hospital_id")     private String hospitalId;
    private String nome;
    private int idade;
    @JsonProperty("queixa_principal") private String queixaPrincipal;
    @JsonProperty("cor_manchester")   private String corManchester;
    @JsonProperty("prioridade_ordem") private int prioridadeOrdem;
    @JsonProperty("tempo_max_espera_min") private int tempoMaxEsperaMin;
    private String status;
    @JsonProperty("criado_em")            private String criadoEm;
    @JsonProperty("classificado_em")      private String classificadoEm;
    @JsonProperty("iniciando_atendimento_em") private String iniciandoAtendimentoEm;
    @JsonProperty("finalizado_em")        private String finalizadoEm;

    public String getTriagemId() { return triagemId; }
    public void setTriagemId(String v) { triagemId = v; }
    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String v) { hospitalId = v; }
    public String getNome() { return nome; }
    public void setNome(String v) { nome = v; }
    public int getIdade() { return idade; }
    public void setIdade(int v) { idade = v; }
    public String getQueixaPrincipal() { return queixaPrincipal; }
    public void setQueixaPrincipal(String v) { queixaPrincipal = v; }
    public String getCorManchester() { return corManchester; }
    public void setCorManchester(String v) { corManchester = v; }
    public int getPrioridadeOrdem() { return prioridadeOrdem; }
    public void setPrioridadeOrdem(int v) { prioridadeOrdem = v; }
    public int getTempoMaxEsperaMin() { return tempoMaxEsperaMin; }
    public void setTempoMaxEsperaMin(int v) { tempoMaxEsperaMin = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getCriadoEm() { return criadoEm; }
    public void setCriadoEm(String v) { criadoEm = v; }
    public String getClassificadoEm() { return classificadoEm; }
    public void setClassificadoEm(String v) { classificadoEm = v; }
    public String getIniciandoAtendimentoEm() { return iniciandoAtendimentoEm; }
    public void setIniciandoAtendimentoEm(String v) { iniciandoAtendimentoEm = v; }
    public String getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(String v) { finalizadoEm = v; }
}
