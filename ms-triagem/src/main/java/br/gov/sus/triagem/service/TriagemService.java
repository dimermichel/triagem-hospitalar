package br.gov.sus.triagem.service;

import br.gov.sus.triagem.client.SqsPublisher;
import br.gov.sus.triagem.dto.TriagemRequestDTO;
import br.gov.sus.triagem.dto.TriagemResponseDTO;
import br.gov.sus.triagem.model.TriagemRecord;
import br.gov.sus.triagem.model.TriagemStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

@ApplicationScoped
public class TriagemService {

    private static final Logger LOG = Logger.getLogger(TriagemService.class);

    @Inject
    SqsPublisher sqsPublisher;

    @Inject
    TriagemRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "hospital.id")
    String hospitalId;

    public TriagemResponseDTO processarTriagem(String triagemId, TriagemRequestDTO request) {
        int idade = calcularIdade(request.getDataNascimento());

        TriagemRecord record = TriagemRecord.builder()
                .triagemId(triagemId)
                .hospitalId(hospitalId)
                .nome(request.getNome())
                .idade(idade)
                .queixaPrincipal(request.getQueixaPrincipal())
                .sinaisVitais(request.getSinaisVitais())
                .intensidadeDor(request.getIntensidadeDor())
                .nivelConsciencia(request.getNivelConsciencia())
                .inicioSubito(request.isInicioSubito())
                .horasSintoma(request.getHorasSintoma())
                .historicoMedico(request.getHistoricoMedico())
                .gestante(request.isGestante())
                .status(TriagemStatus.AGUARDANDO_CLASSIFICACAO)
                .criadoEm(Instant.now())
                .build();

        record.construirChaves();
        repository.salvar(record);

        try {
            String payload = objectMapper.writeValueAsString(record);
            sqsPublisher.publicarParaClassificacao(payload, triagemId);
        } catch (Exception e) {
            LOG.errorf(e, "Erro ao publicar triagem %s no SQS", triagemId);
            throw new RuntimeException("Falha ao encaminhar triagem para classificação", e);
        }

        return TriagemResponseDTO.aceito(triagemId, hospitalId);
    }

    public Optional<TriagemRecord> buscarStatus(String triagemId) {
        return repository.buscarPorId(triagemId);
    }

    public void iniciarAtendimento(String triagemId, String profissionalId) {
        TriagemRecord record = repository.buscarPorId(triagemId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Triagem não encontrada: " + triagemId));

        record.setStatus(TriagemStatus.EM_ATENDIMENTO);
        record.setIniciandoAtendimentoEm(Instant.now());
        record.setProfissionalId(profissionalId);
        repository.atualizar(record);

        LOG.infof("Atendimento iniciado: triagemId=%s, profissional=%s", triagemId, profissionalId);
    }

    public void finalizarAtendimento(String triagemId) {
        TriagemRecord record = repository.buscarPorId(triagemId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("Triagem não encontrada: " + triagemId));

        record.setStatus(TriagemStatus.FINALIZADO);
        record.setFinalizadoEm(Instant.now());
        repository.atualizar(record);

        LOG.infof("Atendimento finalizado: triagemId=%s", triagemId);
    }

    private int calcularIdade(LocalDate dataNascimento) {
        return Period.between(dataNascimento, LocalDate.now()).getYears();
    }
}
