package br.gov.sus.triagem.dto;

import br.gov.sus.triagem.enums.NivelConsciencia;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public class TriagemRequestDTO {

    @NotBlank(message = "Nome do paciente é obrigatório")
    @Size(min = 2, max = 200)
    private String nome;

    @NotNull(message = "Data de nascimento é obrigatória")
    @Past(message = "Data de nascimento deve ser no passado")
    @JsonProperty("data_nascimento")
    private LocalDate dataNascimento;

    @NotBlank(message = "Queixa principal é obrigatória")
    @Size(min = 3, max = 500)
    @JsonProperty("queixa_principal")
    private String queixaPrincipal;

    @NotNull(message = "Sinais vitais são obrigatórios")
    @Valid
    @JsonProperty("sinais_vitais")
    private SinaisVitaisDTO sinaisVitais;

    @NotNull(message = "Intensidade da dor é obrigatória (0-10)")
    @Min(0) @Max(10)
    @JsonProperty("intensidade_dor")
    private Integer intensidadeDor;

    @NotNull(message = "Nível de consciência é obrigatório")
    @JsonProperty("nivel_consciencia")
    private NivelConsciencia nivelConsciencia;

    /** Sintoma de início súbito aumenta urgência */
    @JsonProperty("inicio_subito")
    private boolean inicioSubito;

    /** Tempo de evolução dos sintomas em horas */
    @JsonProperty("horas_sintoma")
    private Integer horasSintoma;

    @JsonProperty("historico_medico")
    private List<String> historicoMedico;

    @JsonProperty("medicamentos_uso")
    private List<String> medicamentosUso;

    @JsonProperty("alergias")
    private List<String> alergias;

    /** Gestante */
    private boolean gestante;

    // ---- getters e setters ----

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }

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

    public List<String> getMedicamentosUso() { return medicamentosUso; }
    public void setMedicamentosUso(List<String> medicamentosUso) { this.medicamentosUso = medicamentosUso; }

    public List<String> getAlergias() { return alergias; }
    public void setAlergias(List<String> alergias) { this.alergias = alergias; }

    public boolean isGestante() { return gestante; }
    public void setGestante(boolean gestante) { this.gestante = gestante; }
}
