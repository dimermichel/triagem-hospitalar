package br.gov.sus.consolidador.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ResumoHospitalDTO {

    @JsonProperty("hospital_id")        private String hospitalId;
    private String data;
    @JsonProperty("total_atendimentos") private int totalAtendimentos;
    @JsonProperty("atendimentos_por_cor") private Map<String, Long> atendimentosPorCor;
    private List<AtendimentoDiarioDTO> atendimentos;
    @JsonProperty("lote_numero")        private int loteNumero;
    @JsonProperty("lote_total")         private int loteTotal;
    @JsonProperty("publicado_em")       private String publicadoEm;

    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String v) { hospitalId = v; }
    public String getData() { return data; }
    public void setData(String v) { data = v; }
    public int getTotalAtendimentos() { return totalAtendimentos; }
    public void setTotalAtendimentos(int v) { totalAtendimentos = v; }
    public Map<String, Long> getAtendimentosPorCor() { return atendimentosPorCor; }
    public void setAtendimentosPorCor(Map<String, Long> v) { atendimentosPorCor = v; }
    public List<AtendimentoDiarioDTO> getAtendimentos() { return atendimentos; }
    public void setAtendimentos(List<AtendimentoDiarioDTO> v) { atendimentos = v; }
    public int getLoteNumero() { return loteNumero; }
    public void setLoteNumero(int v) { loteNumero = v; }
    public int getLoteTotal() { return loteTotal; }
    public void setLoteTotal(int v) { loteTotal = v; }
    public String getPublicadoEm() { return publicadoEm; }
    public void setPublicadoEm(String v) { publicadoEm = v; }
}
