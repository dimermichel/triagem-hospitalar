package br.gov.sus.triagem.model;

import br.gov.sus.triagem.dto.SinaisVitaisDTO;
import br.gov.sus.triagem.enums.NivelConsciencia;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * Entidade DynamoDB da triagem local do hospital.
 *
 * PK: HOSPITAL#<hospitalId>#FILA
 * SK: <cor>#<criadoEm>#<triagemId>
 *
 * A ordenação natural do SK garante prioridade correta:
 * AZUL < AMARELO < LARANJA < VERDE < VERMELHO (lexicográfico invertido para prioridade)
 * Por isso usamos um prefixo numérico: 1-VERMELHO, 2-LARANJA, etc.
 */
@DynamoDbBean
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TriagemRecord {

    private String pk;
    private String sk;
    private String triagemId;
    private String hospitalId;
    private String nome;
    private int idade;
    private String queixaPrincipal;
    private SinaisVitaisDTO sinaisVitais;
    private int intensidadeDor;
    private NivelConsciencia nivelConsciencia;
    private boolean inicioSubito;
    private Integer horasSintoma;
    private List<String> historicoMedico;
    private boolean gestante;
    private TriagemStatus status;
    private String corManchester;
    private int prioridadeOrdem; // 1=Vermelho ... 5=Azul
    private int tempoMaxEsperaMin;
    private Instant criadoEm;
    private Instant classificadoEm;
    private Instant iniciandoAtendimentoEm;
    private Instant finalizadoEm;
    private String profissionalId;

    // Compõe PK e SK para ordenação por prioridade
    public void construirChaves() {
        this.pk = "HOSPITAL#" + hospitalId + "#FILA";
        this.sk = prioridadeOrdem + "#" + corManchester + "#" + criadoEm.toEpochMilli() + "#" + triagemId;
    }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    @DynamoDbAttribute("triagem_id")
    public String getTriagemId() { return triagemId; }
    public void setTriagemId(String triagemId) { this.triagemId = triagemId; }

    @DynamoDbAttribute("hospital_id")
    public String getHospitalId() { return hospitalId; }
    public void setHospitalId(String hospitalId) { this.hospitalId = hospitalId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public int getIdade() { return idade; }
    public void setIdade(int idade) { this.idade = idade; }

    @DynamoDbAttribute("queixa_principal")
    public String getQueixaPrincipal() { return queixaPrincipal; }
    public void setQueixaPrincipal(String queixaPrincipal) { this.queixaPrincipal = queixaPrincipal; }

    @DynamoDbAttribute("sinais_vitais")
    public SinaisVitaisDTO getSinaisVitais() { return sinaisVitais; }
    public void setSinaisVitais(SinaisVitaisDTO sinaisVitais) { this.sinaisVitais = sinaisVitais; }

    @DynamoDbAttribute("intensidade_dor")
    public int getIntensidadeDor() { return intensidadeDor; }
    public void setIntensidadeDor(int intensidadeDor) { this.intensidadeDor = intensidadeDor; }

    @DynamoDbAttribute("nivel_consciencia")
    public NivelConsciencia getNivelConsciencia() { return nivelConsciencia; }
    public void setNivelConsciencia(NivelConsciencia nivelConsciencia) { this.nivelConsciencia = nivelConsciencia; }

    @DynamoDbAttribute("inicio_subito")
    public boolean isInicioSubito() { return inicioSubito; }
    public void setInicioSubito(boolean inicioSubito) { this.inicioSubito = inicioSubito; }

    @DynamoDbAttribute("horas_sintoma")
    public Integer getHorasSintoma() { return horasSintoma; }
    public void setHorasSintoma(Integer horasSintoma) { this.horasSintoma = horasSintoma; }

    @DynamoDbAttribute("historico_medico")
    public List<String> getHistoricoMedico() { return historicoMedico; }
    public void setHistoricoMedico(List<String> historicoMedico) { this.historicoMedico = historicoMedico; }

    public boolean isGestante() { return gestante; }
    public void setGestante(boolean gestante) { this.gestante = gestante; }

    public TriagemStatus getStatus() { return status; }
    public void setStatus(TriagemStatus status) { this.status = status; }

    @DynamoDbAttribute("cor_manchester")
    public String getCorManchester() { return corManchester; }
    public void setCorManchester(String corManchester) { this.corManchester = corManchester; }

    @DynamoDbAttribute("prioridade_ordem")
    public int getPrioridadeOrdem() { return prioridadeOrdem; }
    public void setPrioridadeOrdem(int prioridadeOrdem) { this.prioridadeOrdem = prioridadeOrdem; }

    @DynamoDbAttribute("tempo_max_espera_min")
    public int getTempoMaxEsperaMin() { return tempoMaxEsperaMin; }
    public void setTempoMaxEsperaMin(int tempoMaxEsperaMin) { this.tempoMaxEsperaMin = tempoMaxEsperaMin; }

    @DynamoDbAttribute("criado_em")
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }

    @DynamoDbAttribute("classificado_em")
    public Instant getClassificadoEm() { return classificadoEm; }
    public void setClassificadoEm(Instant classificadoEm) { this.classificadoEm = classificadoEm; }

    @DynamoDbAttribute("iniciando_atendimento_em")
    public Instant getIniciandoAtendimentoEm() { return iniciandoAtendimentoEm; }
    public void setIniciandoAtendimentoEm(Instant iniciandoAtendimentoEm) { this.iniciandoAtendimentoEm = iniciandoAtendimentoEm; }

    @DynamoDbAttribute("finalizado_em")
    public Instant getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(Instant finalizadoEm) { this.finalizadoEm = finalizadoEm; }

    @DynamoDbAttribute("profissional_id")
    public String getProfissionalId() { return profissionalId; }
    public void setProfissionalId(String profissionalId) { this.profissionalId = profissionalId; }

    // Builder interno simples
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final TriagemRecord r = new TriagemRecord();
        public Builder triagemId(String v) { r.triagemId = v; return this; }
        public Builder hospitalId(String v) { r.hospitalId = v; return this; }
        public Builder nome(String v) { r.nome = v; return this; }
        public Builder idade(int v) { r.idade = v; return this; }
        public Builder queixaPrincipal(String v) { r.queixaPrincipal = v; return this; }
        public Builder sinaisVitais(SinaisVitaisDTO v) { r.sinaisVitais = v; return this; }
        public Builder intensidadeDor(int v) { r.intensidadeDor = v; return this; }
        public Builder nivelConsciencia(NivelConsciencia v) { r.nivelConsciencia = v; return this; }
        public Builder inicioSubito(boolean v) { r.inicioSubito = v; return this; }
        public Builder horasSintoma(Integer v) { r.horasSintoma = v; return this; }
        public Builder historicoMedico(java.util.List<String> v) { r.historicoMedico = v; return this; }
        public Builder gestante(boolean v) { r.gestante = v; return this; }
        public Builder status(TriagemStatus v) { r.status = v; return this; }
        public Builder criadoEm(Instant v) { r.criadoEm = v; return this; }
        public TriagemRecord build() { return r; }
    }
}
