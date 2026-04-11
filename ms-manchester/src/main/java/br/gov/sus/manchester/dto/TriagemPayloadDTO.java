package br.gov.sus.manchester.dto;

import br.gov.sus.manchester.enums.NivelConsciencia;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public class TriagemPayloadDTO {

    @JsonProperty("triagem_id")
    private String triagemId;

    @JsonProperty("hospital_id")
    private String hospitalId;

    private String nome;
    private int idade;

    @JsonProperty("queixa_principal")
    private String queixaPrincipal;

    @JsonProperty("sinais_vitais")
    private SinaisVitaisDTO sinaisVitais;

    @JsonProperty("intensidade_dor")
    private Integer intensidadeDor;

    @JsonProperty("nivel_consciencia")
    private NivelConsciencia nivelConsciencia;

    @JsonProperty("inicio_subito")
    private boolean inicioSubito;

    @JsonProperty("horas_sintoma")
    private Integer horasSintoma;

    @JsonProperty("historico_medico")
    private List<String> historicoMedico;

    private boolean gestante;

    @JsonProperty("criado_em")
    private Instant criadoEm;

    // getters e setters

    public String getTriagemId() { return triagemId; }
    public void setTriagemId(String triagemId) { this.triagemId = triagemId; }

    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public int getIdade() { return idade; }
    public void setIdade(int idade) { this.idade = idade; }

    public String getQueixaPrincipal() { return queixaPrincipal; }
    public void setQueixaPrincipal(String queixaPrincipal) { this.queixaPrincipal = queixaPrincipal; }

    public SinaisVitaisDTO getSinaisVitais() { return sinaisVitais; }
    public void setSinaisVitais(SinaisVitaisDTO sinaisVitais) { this.sinaisVitais = sinaisVitais; }

    public Integer getIntensidadeDor() { return intensidadeDor; }
    public void setIntensidadeDor(Integer intensidadeDor) { this.intensidadeDor = intensidadeDor; }

    public NivelConsciencia getNivelConsciencia() { return nivelConsciencia; }
    public void setNivelConsciencia(NivelConsciencia nivelConsciencia) { this.nivelConsciencia = nivelConsciencia; }

    public boolean isInicioSubito() { return inicioSubito; }
    public void setInicioSubito(boolean inicioSubito) { this.inicioSubito = inicioSubito; }

    public Integer getHorasSintoma() { return horasSintoma; }
    public void setHorasSintoma(Integer horasSintoma) { this.horasSintoma = horasSintoma; }

    public List<String> getHistoricoMedico() { return historicoMedico; }
    public void setHistoricoMedico(List<String> historicoMedico) { this.historicoMedico = historicoMedico; }

    public boolean isGestante() { return gestante; }
    public void setGestante(boolean gestante) { this.gestante = gestante; }

    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
}
